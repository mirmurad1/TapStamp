package az.tapstamp.shared.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Təhlükəsizlik", description = "CSRF qorunması üçün köməkçi endpoint-lər")
public class CsrfController {

    @GetMapping("/api/v1/csrf")
    @Operation(
            summary = "CSRF tokenini təmin edir",
            description = """
                    `__Host-XSRF-TOKEN` cookie-sini qoyur (yoxdursa). Frontend bu cookie-nin dəyərini bütün \
                    dəyişdirici sorğularda `X-XSRF-TOKEN` başlığında göndərməlidir.""")
    ResponseEntity<Void> csrf() {
        // Cookie SpaCsrfTokenRequestHandler tərəfindən hər sorğuda avtomatik qoyulur
        return ResponseEntity.noContent().build();
    }
}
