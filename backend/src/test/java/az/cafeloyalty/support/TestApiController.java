package az.cafeloyalty.support;

import java.util.Map;

import az.cafeloyalty.shared.error.ApiException;
import az.cafeloyalty.shared.error.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Yalnız testlər üçün: xəta formatını və validasiyanı yoxlamaq üçün endpoint-lər. Skan olunmur, açıq import edilir. */
@TestComponent
@RestController
@RequestMapping("/api/v1/public/test")
public class TestApiController {

    @GetMapping("/api-error")
    void apiError() {
        throw new ApiException(ErrorCode.RATE_LIMITED, new Object[0], Map.of("retryAfterSeconds", 30));
    }

    @GetMapping("/boom")
    void boom() {
        throw new IllegalStateException("secret internal detail");
    }

    @PostMapping("/echo")
    EchoRequest echo(@Valid @RequestBody EchoRequest request) {
        return request;
    }

    public record EchoRequest(@NotBlank String name, @Size(min = 2, max = 5) String code) {
    }
}
