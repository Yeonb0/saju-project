package com.sajuppugi.fortune.reading.api;
import com.sajuppugi.common.api.*;
import com.sajuppugi.fortune.reading.application.ElementPreviewService;
import jakarta.validation.Valid; import jakarta.validation.constraints.NotNull; import java.util.UUID;
import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/fortune/element-preview") public class ElementPreviewController {
 private final ElementPreviewService previews; public ElementPreviewController(ElementPreviewService p){previews=p;}
 @PostMapping public ApiResponse<ElementPreviewService.Preview> get(Authentication a,@Valid @RequestBody Request r){return ApiResponse.of(previews.get(user(a),r.personId()));}
 private UUID user(Authentication a){try{return UUID.fromString(a.getName());}catch(Exception e){throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED);}}
 public record Request(@NotNull UUID personId) {}
}
