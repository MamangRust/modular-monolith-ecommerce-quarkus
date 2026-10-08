package com.sanedge.shipping_address.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sanedge.shipping_address.domain.requests.FindAllShippingAddress;
import com.sanedge.shipping_address.entity.ShippingAddress;

import com.sanedge.common.test.PostgreSqlResource;
import com.sanedge.common.test.ReactiveSequential;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(PostgreSqlResource.class)
@TestReactiveTransaction
class ShippingAddressRepositoryTest {

    @Inject
    ShippingAddressQueryRepository shippingAddressQueryRepo;

    @Inject
    ShippingAddressCommandRepository shippingAddressCommandRepo;

    private Uni<ShippingAddress> createAndPersistShippingAddress(Integer orderId, String kota, String provinsi) {
        ShippingAddress address = new ShippingAddress();
        address.setOrderId(orderId);
        address.setAlamat("Jl. Contoh " + kota);
        address.setKota(kota);
        address.setProvinsi(provinsi);
        address.setNegara("Indonesia");
        address.setCourier("JNE");
        address.setShippingMethod("REGULER");
        address.setShippingCost(15000);
        return shippingAddressQueryRepo.persist(address).replaceWith(address);
    }

    // A Hibernate Reactive session is not thread-safe, so entities must be
    // persisted sequentially instead of via Uni.combine().all()/Uni.join().all().
    private Uni<List<ShippingAddress>> createAndPersistAddresses(Object[][] specs) {
        Uni<List<ShippingAddress>> chain = Uni.createFrom().item(new ArrayList<ShippingAddress>());
        for (Object[] spec : specs) {
            chain = chain.chain(list -> createAndPersistShippingAddress(
                    (Integer) spec[0], (String) spec[1], (String) spec[2])
                    .invoke(list::add)
                    .replaceWith(list));
        }
        return chain;
    }

    private Uni<Void> trashSequentially(List<ShippingAddress> addresses, int... indices) {
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (int index : indices) {
            ShippingAddress address = addresses.get(index);
            chain = chain.chain(() -> shippingAddressCommandRepo.trash(address.id).replaceWithVoid());
        }
        return chain;
    }

    private FindAllShippingAddress buildFindAllRequest(int page, int pageSize, String search) {
        FindAllShippingAddress req = new FindAllShippingAddress();
        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(search);
        return req;
    }

    @Test
    void testCreateAndFindById(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistShippingAddress(1, "Jakarta Selatan", "DKI Jakarta")
                .invoke(saved -> {
                    assertThat(saved).isNotNull();
                    assertThat(saved.id).isNotNull();
                    assertThat(saved.getKota()).isEqualTo("Jakarta Selatan");
                })
                .chain(saved -> shippingAddressQueryRepo.findByIdNative(saved.id))
                .invoke(opt -> {
                    assertThat(opt).isPresent();
                    assertThat(opt.get().getKota()).isEqualTo("Jakarta Selatan");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindByOrderId(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistShippingAddress(42, "Bandung", "Jawa Barat")
                .chain(ignored -> shippingAddressQueryRepo.findByOrderId(42))
                .invoke(opt -> {
                    assertThat(opt).isPresent();
                    assertThat(opt.get().getProvinsi()).isEqualTo("Jawa Barat");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindByOrderIdReturnsEmptyWhenNotFound(UniAsserter asserter) {
        asserter.execute(() -> shippingAddressQueryRepo.findByOrderId(99999)
                .invoke(opt -> assertThat(opt).isEmpty())
                .replaceWithVoid());
    }

    @Test
    void testTrashShippingAddress(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistShippingAddress(2, "Surabaya", "Jawa Timur")
                .invoke(saved -> assertThat(saved.getDeletedAt()).isNull())
                .chain(saved -> shippingAddressCommandRepo.trash(saved.id))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testTrashShippingAddressReturnsNullIfAlreadyTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistShippingAddress(3, "Semarang", "Jawa Tengah")
                .chain(saved -> shippingAddressCommandRepo.trash(saved.id)
                        .chain(ignored -> shippingAddressCommandRepo.trash(saved.id)))
                .invoke(trashedAgain -> assertThat(trashedAgain).isNull())
                .replaceWithVoid());
    }

    @Test
    void testTrashShippingAddressReturnsNullIfNotFound(UniAsserter asserter) {
        asserter.execute(() -> shippingAddressCommandRepo.trash(99999L)
                .invoke(trashed -> assertThat(trashed).isNull())
                .replaceWithVoid());
    }

    @Test
    void testRestoreShippingAddress(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistShippingAddress(4, "Yogyakarta", "DI Yogyakarta")
                .chain(saved -> shippingAddressCommandRepo.trash(saved.id)
                        .chain(ignored -> shippingAddressCommandRepo.restore(saved.id)))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testRestoreShippingAddressReturnsNullIfNotTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistShippingAddress(5, "Malang", "Jawa Timur")
                .chain(saved -> shippingAddressCommandRepo.restore(saved.id))
                .invoke(restored -> assertThat(restored).isNull())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanent(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistShippingAddress(6, "Medan", "Sumatera Utara")
                .chain(saved -> shippingAddressCommandRepo.trash(saved.id)
                        .chain(ignored -> shippingAddressCommandRepo.deletePermanent(saved.id))
                        .chain(deleted -> {
                            assertThat(deleted).isNotNull();
                            return shippingAddressQueryRepo.findByIdNative(saved.id);
                        }))
                .invoke(checkDb -> assertThat(checkDb).isEmpty())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanentFailsIfNotTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistShippingAddress(7, "Makassar", "Sulawesi Selatan")
                .chain(saved -> shippingAddressCommandRepo.deletePermanent(saved.id)
                        .chain(deleted -> {
                            assertThat(deleted).isNull();
                            return shippingAddressQueryRepo.findByIdNative(saved.id);
                        }))
                .invoke(checkDb -> assertThat(checkDb).isPresent())
                .replaceWithVoid());
    }

    @Test
    void testRestoreAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistAddresses(new Object[][] {
                { 10, "Bekasi", "Jawa Barat" },
                { 11, "Depok", "Jawa Barat" },
                { 12, "Tangerang", "Banten" } })
                .chain(addresses -> trashSequentially(addresses, 0, 1).replaceWith(addresses))
                .chain(addresses -> shippingAddressCommandRepo.restoreAllDeleted()
                        .invoke(result -> assertThat(result).isTrue()))
                .replaceWithVoid());
    }

    @Test
    void testDeleteAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistAddresses(new Object[][] {
                { 20, "Bogor", "Jawa Barat" },
                { 21, "Cirebon", "Jawa Barat" },
                { 22, "Garut", "Jawa Barat" } })
                .chain(addresses -> trashSequentially(addresses, 0, 1).replaceWith(addresses))
                .chain(addresses -> shippingAddressCommandRepo.deleteAllDeleted()
                        .invoke(result -> assertThat(result).isTrue()))
                .replaceWithVoid());
    }

    @Test
    void testFindShippingAddressesExcludesTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistAddresses(new Object[][] {
                { 30, "Jakarta Pusat", "DKI Jakarta" },
                { 31, "Jakarta Utara", "DKI Jakarta" } })
                .chain(addresses -> trashSequentially(addresses, 1).replaceWith(addresses))
                .chain(addresses -> shippingAddressQueryRepo.findShippingAddresses(buildFindAllRequest(1, 10, null)))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getKota()).isEqualTo("Jakarta Pusat");
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindActiveShippingAddresses(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistAddresses(new Object[][] {
                { 32, "Solo", "Jawa Tengah" },
                { 33, "Palembang", "Sumatera Selatan" } })
                .chain(addresses -> trashSequentially(addresses, 1).replaceWith(addresses))
                .chain(addresses -> shippingAddressQueryRepo
                        .findActiveShippingAddresses(buildFindAllRequest(1, 10, null)))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getKota()).isEqualTo("Solo");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindTrashedShippingAddresses(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistAddresses(new Object[][] {
                { 34, "Denpasar", "Bali" },
                { 35, "Kuta", "Bali" } })
                .chain(addresses -> trashSequentially(addresses, 0).replaceWith(addresses))
                .chain(addresses -> shippingAddressQueryRepo
                        .findTrashedShippingAddresses(buildFindAllRequest(1, 10, null)))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getKota()).isEqualTo("Denpasar");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindShippingAddressesWithSearchKeyword(UniAsserter asserter) {
        asserter.execute(() -> ReactiveSequential.sequential(
                createAndPersistShippingAddress(40, "Bandung", "Jawa Barat"),
                createAndPersistShippingAddress(41, "Bekasi", "Jawa Barat"),
                createAndPersistShippingAddress(42, "Medan", "Sumatera Utara"))
                .chain(() -> shippingAddressQueryRepo.findShippingAddresses(buildFindAllRequest(1, 10, "Barat")))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindTrashedShippingAddressesWithSearchKeyword(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistAddresses(new Object[][] {
                { 43, "Samarinda", "Kalimantan Timur" },
                { 44, "Balikpapan", "Kalimantan Timur" },
                { 45, "Pontianak", "Kalimantan Barat" } })
                .chain(addresses -> trashSequentially(addresses, 0, 1).replaceWith(addresses))
                .chain(addresses -> shippingAddressQueryRepo
                        .findTrashedShippingAddresses(buildFindAllRequest(1, 10, "Timur")))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindShippingAddressesWithPagination(UniAsserter asserter) {
        asserter.execute(() -> ReactiveSequential.sequential(
                createAndPersistShippingAddress(50, "Kota1", "Prov1"),
                createAndPersistShippingAddress(51, "Kota2", "Prov1"),
                createAndPersistShippingAddress(52, "Kota3", "Prov1"),
                createAndPersistShippingAddress(53, "Kota4", "Prov1"),
                createAndPersistShippingAddress(54, "Kota5", "Prov1"))
                .chain(() -> shippingAddressQueryRepo.findShippingAddresses(buildFindAllRequest(1, 2, null)))
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(page1 -> shippingAddressQueryRepo.findShippingAddresses(buildFindAllRequest(2, 2, null))
                        .invoke(page2 -> {
                            assertThat(page2.getData()).hasSize(2);
                            assertThat(page2.getData().get(0).getKota()).isNotIn(
                                    page1.getData().get(0).getKota(),
                                    page1.getData().get(1).getKota());
                        }))
                .replaceWithVoid());
    }

    @Test
    void testFindShippingAddressesWithEmptySearchReturnsAll(UniAsserter asserter) {
        asserter.execute(() -> ReactiveSequential.sequential(
                createAndPersistShippingAddress(60, "KotaA", "ProvA"),
                createAndPersistShippingAddress(61, "KotaB", "ProvB"))
                .chain(() -> shippingAddressQueryRepo.findShippingAddresses(buildFindAllRequest(1, 10, "")))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindShippingAddressesWithNullSearchReturnsAll(UniAsserter asserter) {
        asserter.execute(() -> ReactiveSequential.sequential(
                createAndPersistShippingAddress(62, "KotaC", "ProvC"),
                createAndPersistShippingAddress(63, "KotaD", "ProvD"))
                .chain(() -> shippingAddressQueryRepo.findShippingAddresses(buildFindAllRequest(1, 10, null)))
                .invoke(result -> assertThat(result.getData()).hasSize(2))
                .replaceWithVoid());
    }

    @Test
    void testFindShippingAddressesSearchReturnsEmptyWhenNoMatch(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistShippingAddress(70, "Jakarta", "DKI")
                .chain(ignored -> shippingAddressQueryRepo.findShippingAddresses(buildFindAllRequest(1, 10, "TidakAda")))
                .invoke(result -> {
                    assertThat(result.getData()).isEmpty();
                    assertThat(result.getTotalRecords()).isZero();
                })
                .replaceWithVoid());
    }
}
