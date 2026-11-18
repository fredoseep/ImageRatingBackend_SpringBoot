package com.example.rating;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(Problem.class) public ResponseEntity<?> problem(Problem p){return ResponseEntity.status(p.status).body(Map.of("error",p.getMessage()));}
    @ExceptionHandler(HttpMessageNotReadableException.class) public ResponseEntity<?> badJson(){return ResponseEntity.badRequest().body(Map.of("error","JSON或Base64格式错误"));}
}
