package az.tapstamp.shared.persistence;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Şirkət (tenant) bazasının MyBatis mapper-i: sorğular cari tenant-ın bazasında icra olunur ({@code TenantContext}).
 * SQL {@code resources/mapper/tenant/**}{@code /<Ad>.xml} faylındadır.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantMapper {
}
