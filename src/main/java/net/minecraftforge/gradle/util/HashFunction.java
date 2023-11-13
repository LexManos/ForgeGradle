/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.zip.Adler32;
import java.util.zip.Checksum;

import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Different hash functions.
 *
 * <p>All of these hashing functions are standardized, and is required to be implemented by all JREs. However, {@link
 * MessageDigest#getInstance(String)} declares as throwing the checked exception of {@link NoSuchAlgorithmException}.</p>
 *
 * <p>This class offers a cleaner method to retrieve an instance of these hashing functions, without having to wrap in a
 * {@code try}-{@code catch} block.</p>
 */
@ApiStatus.Internal
public enum HashFunction {
    MD5("md5", 32),
    SHA1("SHA-1", 40),
    SHA256("SHA-256", 64),
    SHA512("SHA-512", 128),
    CRC32("CRC32", 8, java.util.zip.CRC32::new),
    CRC32C("CRC32C", 8, java.util.zip.CRC32C::new),
    ALDER32("ALDER32", 8, Adler32::new);

    private final String pad;
    private final Supplier<MessageDigest> factory;

    HashFunction(String algo, int length) {
        this.pad = String.format(Locale.ROOT, "%0" + length + "d", 0);
        this.factory = () -> {
            try {
                return MessageDigest.getInstance(algo);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e); // Never happens
            }
        };
    }

    HashFunction(String algo, int length, Supplier<Checksum> checksum) {
        this.pad = String.format(Locale.ROOT, "%0" + length + "d", 0);
        this.factory = () -> new ChecksumMessageDigest(algo, checksum.get());
    }

    public String getExtension() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public MessageDigest get() {
        return this.factory.get();
    }

    public String hash(File file) throws IOException {
        return hash(file.toPath());
    }

    public String hash(Path file) throws IOException {
        return hash(Files.readAllBytes(file));
    }

    public String hash(Iterable<File> files) throws IOException {
        MessageDigest hash = get();

        for (File file : files) {
            if (!file.exists())
                continue;
            hash.update(Files.readAllBytes(file.toPath()));
        }
        return pad(new BigInteger(1, hash.digest()).toString(16));
    }

    public String hash(@Nullable String data) {
        return hash(data == null ? new byte[0] : data.getBytes(StandardCharsets.UTF_8));
    }

    public String hash(InputStream stream) throws IOException {
        return hash(IOUtils.toByteArray(stream));
    }

    public String hash(byte[] data) {
        return pad(new BigInteger(1, get().digest(data)).toString(16));
    }

    public String pad(String hash) {
        return (pad + hash).substring(hash.length());
    }
}
