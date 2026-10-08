package az.cafeloyalty.security.crypto;

/** Kriptoqrafik əməliyyatın uğursuzluğu. Mesajda heç vaxt açar və ya açıq mətn olmur. */
public class CryptoException extends RuntimeException {

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
