package com.sanedge.common.adapter.model;

/**
 * Hasil verifikasi kredensial. {@code user} hanya terisi bila {@code valid}
 * bernilai {@code true}.
 */
public record VerifyPasswordResult(boolean valid, User user) {
}
