package az.tapstamp.plan;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controller-in (sinif və ya metod) şirkətdə açıq olmalı əlavəsi. Yoxdursa — {@code PLAN_FEATURE_UNAVAILABLE} (403).
 * Metoddakı annotasiya sinifdəkini əvəz edir.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresAddon {
    Addon value();
}
