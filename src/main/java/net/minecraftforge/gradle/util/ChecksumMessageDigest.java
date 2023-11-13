package net.minecraftforge.gradle.util;

import java.security.MessageDigest;
import java.util.zip.Checksum;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
class ChecksumMessageDigest extends MessageDigest {
    private final Checksum checksum;
    public ChecksumMessageDigest(String algorithm, Checksum checksum) {
        super(algorithm);
        this.checksum = checksum;
    }

    @Override
    protected void engineUpdate(byte input) {
        checksum.update((int)input);
    }

    @Override
    protected void engineUpdate(byte[] input, int offset, int len) {
        checksum.update(input, offset, len);
    }

    @Override
    protected byte[] engineDigest() {
        long v = checksum.getValue();
        engineReset();
        return new byte[] {
           (byte)(v >>> 24),
           (byte)(v >>> 16),
           (byte)(v >>>  8),
           (byte)(v >>>  0),
        };
    }

    @Override
    protected void engineReset() {
        checksum.reset();
    }

    @Override
    protected int engineGetDigestLength() {
        return 4;
    }
}
