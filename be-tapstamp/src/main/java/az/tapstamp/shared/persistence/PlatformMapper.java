package az.tapstamp.shared.persistence;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Platforma bazasının ({@code loyalty_platform}) MyBatis mapper-i. SQL {@code resources/mapper/platform/**}{@code /<Ad>.xml}
 * faylındadır.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PlatformMapper {
}
