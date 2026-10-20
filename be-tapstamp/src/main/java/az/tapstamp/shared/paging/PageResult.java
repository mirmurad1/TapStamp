package az.tapstamp.shared.paging;

import java.util.List;
import java.util.function.Function;

/** Səhifənin elementləri və ümumi say (UI səhifə sayını buradan hesablayır). */
public record PageResult<T>(List<T> items, long total, int page, int size) {

    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        return new PageResult<>(items.stream().<R>map(mapper).toList(), total, page, size);
    }
}
