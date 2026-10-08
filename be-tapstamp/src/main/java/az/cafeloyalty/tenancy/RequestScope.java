package az.cafeloyalty.tenancy;

import jakarta.servlet.http.HttpServletRequest;

/** Sorğunun hansı domendə gəldiyi: şirkətin domeni, platformanın domeni və ya domendən asılı olmayan yol. */
public enum RequestScope {
    TENANT,
    PLATFORM,
    /** Health check və dev sənədləri kimi domendən asılı olmayan yollar. */
    NONE;

    public static final String ATTRIBUTE = RequestScope.class.getName();

    public static RequestScope of(HttpServletRequest request) {
        return request.getAttribute(ATTRIBUTE) instanceof RequestScope scope ? scope : NONE;
    }
}
