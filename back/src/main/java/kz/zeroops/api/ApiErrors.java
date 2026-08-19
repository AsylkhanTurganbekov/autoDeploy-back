package kz.zeroops.api;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*; import org.springframework.web.bind.MethodArgumentNotValidException; import org.springframework.web.bind.annotation.*; import org.springframework.web.server.ResponseStatusException;
@RestControllerAdvice public class ApiErrors {
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<ProblemDetail> status(ResponseStatusException e){ProblemDetail p=ProblemDetail.forStatusAndDetail(e.getStatusCode(),e.getReason()==null?"Request failed":e.getReason());return ResponseEntity.status(e.getStatusCode()).body(p);}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ProblemDetail> invalid(MethodArgumentNotValidException e){String detail=e.getBindingResult().getFieldErrors().stream().findFirst().map(x->x.getField()+": "+x.getDefaultMessage()).orElse("Validation failed");return ResponseEntity.unprocessableEntity().body(ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,detail));}
}
