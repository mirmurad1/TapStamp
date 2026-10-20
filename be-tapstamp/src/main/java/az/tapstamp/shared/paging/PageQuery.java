package az.tapstamp.shared.paging;

import java.util.List;

import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;

/**
 * Siyahıların səhifələnməsi (pagination — böyük siyahını hissə-hissə göstərmək): {@code page} 0-dan başlayır,
 * {@code size} yalnız {@link #SIZES} dəyərlərindən biri ola bilər (UI-dakı seçimlərlə eyni).
 */
public record PageQuery(int page, int size) {

    public static final List<Integer> SIZES = List.of(5, 10, 20, 50);
    public static final int DEFAULT_SIZE = 10;
    /** Controller-lərdə {@code @RequestParam(defaultValue = ...)} üçün. */
    public static final String DEFAULT_SIZE_PARAM = "10";

    public PageQuery {
        if (page < 0 || !SIZES.contains(size)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
    }

    public static PageQuery of(int page, int size) {
        return new PageQuery(page, size);
    }

    public int limit() {
        return size;
    }

    public long offset() {
        return (long) page * size;
    }

    public <T> PageResult<T> result(List<T> items, long total) {
        return new PageResult<>(items, total, page, size);
    }

    /** Yaddaşdakı kiçik siyahıdan (məs. icazəyə görə Java-da süzülən komanda) səhifə kəsir. */
    public <T> PageResult<T> slice(List<T> all) {
        int from = (int) Math.min(offset(), all.size());
        return result(List.copyOf(all.subList(from, Math.min(from + size, all.size()))), all.size());
    }
}
