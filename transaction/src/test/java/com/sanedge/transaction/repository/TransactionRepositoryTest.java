package com.sanedge.transaction.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sanedge.transaction.domain.requests.FindAllTransactionByMerchantRequest;
import com.sanedge.transaction.domain.requests.FindAllTransactionRequest;
import com.sanedge.transaction.entity.Transaction;
import com.sanedge.transaction.enums.PaymentStatus;

import com.sanedge.common.test.PostgreSqlResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(PostgreSqlResource.class)
@TestReactiveTransaction
class TransactionRepositoryTest {

    @Inject
    TransactionQueryRepository transactionQueryRepo;

    @Inject
    TransactionCommandRepository transactionCommandRepo;

    private Uni<Long> persist(String paymentMethod, int orderId, int merchantId, int amount, PaymentStatus status) {
        Transaction tx = new Transaction();
        tx.setOrderId(orderId);
        tx.setMerchantId(merchantId);
        tx.setPaymentMethod(paymentMethod);
        tx.setAmount(amount);
        tx.setStatus(status);
        return transactionQueryRepo.persist(tx).map(Transaction::getId);
    }

    private Uni<Long> persist(String paymentMethod, int orderId, int merchantId) {
        return persist(paymentMethod, orderId, merchantId, 100000, PaymentStatus.PENDING);
    }

    private Uni<Void> clean() {
        return transactionQueryRepo.deleteAll().replaceWithVoid();
    }

    private FindAllTransactionRequest findAllReq(int page, int size, String search) {
        FindAllTransactionRequest r = new FindAllTransactionRequest();
        r.setPage(page);
        r.setPageSize(size);
        r.setSearch(search == null ? "" : search);
        return r;
    }

    private FindAllTransactionByMerchantRequest findAllByMerchantReq(int merchantId, int page, int size,
            String search) {
        FindAllTransactionByMerchantRequest r = new FindAllTransactionByMerchantRequest();
        r.setMerchantId(merchantId);
        r.setPage(page);
        r.setPageSize(size);
        r.setSearch(search == null ? "" : search);
        return r;
    }

    @Test
    void testCreateAndFindById(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Credit Card", 1, 1))
                .chain(id -> transactionQueryRepo.findById(id))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getPaymentMethod()).isEqualTo("Credit Card");
                    assertThat(found.getAmount()).isEqualTo(100000);
                    assertThat(found.getStatus()).isEqualTo(PaymentStatus.PENDING);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindTransactionById(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Bank Transfer", 2, 1))
                .chain(id -> transactionQueryRepo.findTransactionById(id))
                .invoke(opt -> {
                    assertThat(opt).isPresent();
                    assertThat(opt.get().getPaymentMethod()).isEqualTo("Bank Transfer");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindTransactionByIdReturnsEmptyIfNotFound(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> transactionQueryRepo.findTransactionById(999999L))
                .invoke(opt -> assertThat(opt).isEmpty())
                .replaceWithVoid());
    }

    @Test
    void testFindTransactionByIdReturnsEmptyIfTrashed(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("E-Wallet", 3, 1))
                .chain(id -> transactionCommandRepo.trashed(id)
                        .chain(() -> transactionQueryRepo.findTransactionById(id)))
                .invoke(opt -> assertThat(opt).isEmpty())
                .replaceWithVoid());
    }

    @Test
    void testFindByOrderId(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Credit Card", 42, 1, 50000, PaymentStatus.SUCCESS))
                .chain(() -> transactionQueryRepo.findByOrderId(42))
                .invoke(opt -> {
                    assertThat(opt).isPresent();
                    assertThat(opt.get().getOrderId()).isEqualTo(42);
                    assertThat(opt.get().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindByOrderIdReturnsEmptyWhenNotFound(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> transactionQueryRepo.findByOrderId(99999))
                .invoke(opt -> assertThat(opt).isEmpty())
                .replaceWithVoid());
    }

    @Test
    void testFindByIdReturnsNullWhenNotFound(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> transactionQueryRepo.findById(999999L))
                .invoke(n -> assertThat(n).isNull())
                .replaceWithVoid());
    }

    @Test
    void testTrashTransaction(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Credit Card", 10, 1))
                .chain(id -> transactionCommandRepo.trashed(id))
                .invoke(t -> {
                    assertThat(t).isNotNull();
                    assertThat(t.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testTrashTransactionReturnsNullIfAlreadyTrashed(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Credit Card", 11, 1))
                .chain(id -> transactionCommandRepo.trashed(id)
                        .chain(() -> transactionCommandRepo.trashed(id)))
                .invoke(t -> assertThat(t).isNull())
                .replaceWithVoid());
    }

    @Test
    void testTrashTransactionReturnsNullIfNotFound(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> transactionCommandRepo.trashed(99999L))
                .invoke(t -> assertThat(t).isNull())
                .replaceWithVoid());
    }

    @Test
    void testRestoreTransaction(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Bank Transfer", 20, 1))
                .chain(id -> transactionCommandRepo.trashed(id)
                        .chain(() -> transactionCommandRepo.restore(id)))
                .invoke(r -> {
                    assertThat(r).isNotNull();
                    assertThat(r.getDeletedAt()).isNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testRestoreTransactionReturnsNullIfNotTrashed(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Bank Transfer", 21, 1))
                .chain(id -> transactionCommandRepo.restore(id))
                .invoke(r -> assertThat(r).isNull())
                .replaceWithVoid());
    }

    @Test
    void testRestoreTransactionReturnsNullIfNotFound(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> transactionCommandRepo.restore(99999L))
                .invoke(r -> assertThat(r).isNull())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanent(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("E-Wallet", 30, 1))
                .chain(id -> transactionCommandRepo.trashed(id)
                        .chain(() -> transactionCommandRepo.deletePermanent(id))
                        .chain(deleted -> transactionQueryRepo.findByOrderId(30)))
                .invoke(opt -> assertThat(opt).isEmpty())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanentFailsIfNotTrashed(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("E-Wallet", 31, 1))
                .chain(id -> transactionCommandRepo.deletePermanent(id))
                .invoke(d -> assertThat(d).isNull())
                .replaceWithVoid());
    }

    @Test
    void testDeleteByOrderPermanent(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Credit Card", 88, 1)
                        .chain(id -> transactionCommandRepo.trashed(id).replaceWithVoid()))
                .chain(() -> persist("Bank Transfer", 88, 2)
                        .chain(id -> transactionCommandRepo.trashed(id).replaceWithVoid()))
                .chain(() -> transactionCommandRepo.deleteByOrderPermanent(88L))
                .invoke(r -> assertThat(r).isTrue())
                .chain(() -> transactionQueryRepo.findByOrderId(88))
                .invoke(opt -> assertThat(opt).isEmpty())
                .replaceWithVoid());
    }

    @Test
    void testRestoreAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Credit Card", 50, 1)
                        .chain(id -> transactionCommandRepo.trashed(id).replaceWithVoid()))
                .chain(() -> persist("Bank Transfer", 51, 1)
                        .chain(id -> transactionCommandRepo.trashed(id).replaceWithVoid()))
                .chain(() -> transactionCommandRepo.restoreAllDeleted())
                .invoke(r -> assertThat(r).isTrue())
                .replaceWithVoid());
    }

    @Test
    void testDeleteAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Credit Card", 60, 1)
                        .chain(id -> transactionCommandRepo.trashed(id).replaceWithVoid()))
                .chain(() -> persist("Bank Transfer", 61, 1)
                        .chain(id -> transactionCommandRepo.trashed(id).replaceWithVoid()))
                .chain(() -> persist("E-Wallet", 62, 1))
                .chain(() -> transactionCommandRepo.deleteAllDeleted())
                .invoke(r -> assertThat(r).isTrue())
                .replaceWithVoid());
    }

    @Test
    void testFindActiveTransactions(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Credit Card", 70, 1))
                .chain(() -> persist("Bank Transfer", 71, 1))
                .chain(() -> transactionQueryRepo.findActiveTransactions(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isEqualTo(2))
                .replaceWithVoid());
    }

    @Test
    void testFindTrashedTransactions(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Credit Card", 80, 1))
                .chain(() -> persist("Bank Transfer", 81, 1))
                .chain(() -> transactionQueryRepo.findTrashedTransactions(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .replaceWithVoid());
    }

    @Test
    void testFindTransactionsWithSearchKeyword(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Credit Card", 90, 1))
                .chain(() -> persist("Bank Transfer", 91, 1))
                .chain(() -> persist("E-Wallet", 92, 1))
                .chain(() -> transactionQueryRepo.findTransactions(findAllReq(1, 10, "Transfer")))
                .invoke(r -> {
                    assertThat(r.getTotalRecords()).isEqualTo(1);
                    assertThat(r.getData().get(0).getPaymentMethod()).isEqualTo("Bank Transfer");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindTransactionsWithPagination(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Credit Card", 100, 1))
                .chain(() -> persist("Bank Transfer", 101, 1))
                .chain(() -> persist("E-Wallet", 102, 1))
                .chain(() -> persist("Crypto", 103, 1))
                .chain(() -> persist("Voucher", 104, 1))
                .chain(() -> transactionQueryRepo.findTransactions(findAllReq(1, 2, "")))
                .map(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                    return page1;
                })
                .chain(page1 -> transactionQueryRepo.findTransactions(findAllReq(2, 2, "")))
                .invoke(page2 -> assertThat(page2.getData()).hasSize(2))
                .replaceWithVoid());
    }

    @Test
    void testFindTransactionsByMerchant(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Credit Card", 110, 1))
                .chain(() -> persist("Credit Card", 111, 2))
                .chain(() -> persist("Bank Transfer", 112, 1))
                .chain(() -> transactionQueryRepo.findTransactionsByMerchant(findAllByMerchantReq(1, 1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isEqualTo(2))
                .replaceWithVoid());
    }

    @Test
    void testFindTransactionsByMerchantWithSearch(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persist("Credit Card", 120, 1))
                .chain(() -> persist("Bank Transfer", 121, 1))
                .chain(() -> persist("Bank Transfer", 122, 2))
                .chain(() -> transactionQueryRepo
                        .findTransactionsByMerchant(findAllByMerchantReq(1, 1, 10, "Transfer")))
                .invoke(r -> {
                    assertThat(r.getTotalRecords()).isEqualTo(1);
                    assertThat(r.getData().get(0).getMerchantId()).isEqualTo(1);
                    assertThat(r.getData().get(0).getPaymentMethod()).isEqualTo("Bank Transfer");
                })
                .replaceWithVoid());
    }
}