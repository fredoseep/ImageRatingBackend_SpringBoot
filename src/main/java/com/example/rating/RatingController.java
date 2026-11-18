package com.example.rating;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@RestController
public class RatingController {
    private final RatingService service;
    public RatingController(RatingService service){this.service=service;}
    public record Heartbeat(String session,Boolean online) {}
    @GetMapping("/health") public Map<String,Boolean> health(){return Map.of("ok",true);}
    @PostMapping("/api/tasks") @ResponseStatus(HttpStatus.CREATED)
    public RatingService.Task upload(@RequestBody RatingService.Upload body){return service.create(body);}
    @GetMapping("/api/tasks/{id}") public RatingService.Task get(@PathVariable("id") String id){return service.get(id);}
    @GetMapping("/api/admin/tasks") public Map<String,Object> list(){return service.listing();}
    @PostMapping("/api/admin/heartbeat") public Map<String,Object> heartbeat(@RequestBody Heartbeat h){if(h.online()==null)throw new Problem(400,"需要online");return service.heartbeat(h.session(),h.online());}
    @GetMapping("/api/admin/tasks/{id}/image") public ResponseEntity<byte[]> photo(@PathVariable("id") String id){var p=service.photo(id);return ResponseEntity.ok().contentType(MediaType.parseMediaType(p.mime())).body(p.bytes());}
    @PostMapping("/api/admin/tasks/{id}/rate") public RatingService.Task rate(@PathVariable("id") String id,@RequestBody RatingService.Rating rating){return service.rate(id,rating);}
}
