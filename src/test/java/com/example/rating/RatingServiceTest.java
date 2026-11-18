package com.example.rating;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

class RatingServiceTest {
    static class TestClock extends Clock {
        long ms=100000;public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return Instant.ofEpochMilli(ms);}
        void advance(long seconds){ms+=seconds*1000;}
    }
    TestClock clock;RatingService service;JdbcTemplate db;
    final byte[] png={(byte)137,80,78,71,13,10,26,10,1};
    @BeforeEach void setup(){var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);db=new JdbcTemplate(ds);clock=new TestClock();service=new RatingService(db,60,clock);}
    RatingService.Task upload(){return service.create(new RatingService.Upload(UUID.randomUUID().toString(),"用户描述",png));}
    @Test void offlineImmediatelyFallsBack(){var t=upload();assertEquals("fallback",t.status());assertEquals("operator_offline",t.reason());assertNull(t.score());}
    @Test void onlineCanRateAndReadImage(){service.heartbeat("operator-1",true);var t=upload();assertEquals("pending",t.status());assertArrayEquals(png,service.photo(t.id()).bytes());var r=service.rate(t.id(),new RatingService.Rating(87.5,"构图很好"));assertEquals("rated",r.status());assertEquals(87.5,r.score());assertEquals("构图很好",service.get(t.id()).comment());assertEquals(409,assertThrows(Problem.class,()->service.rate(t.id(),new RatingService.Rating(1.0,"重复"))).status);}
    @Test void leaseExpiresAfter15Seconds(){service.heartbeat("operator-1",true);var t=upload();clock.advance(15);assertEquals("operator_offline",service.get(t.id()).reason());assertEquals(409,assertThrows(Problem.class,()->service.rate(t.id(),new RatingService.Rating(80.0,"迟到"))).status);}
    @Test void timeoutWinsEvenWhenHumanStillOnline(){service.heartbeat("operator-1",true);var t=upload();clock.advance(60);service.heartbeat("operator-1",true);assertEquals("human_timeout",service.get(t.id()).reason());}
    @Test void oneTabPauseDoesNotStopOtherTab(){service.heartbeat("operator-1",true);service.heartbeat("operator-2",true);service.heartbeat("operator-1",false);assertEquals("pending",upload().status());service.heartbeat("operator-2",false);assertEquals("fallback",upload().status());}
    @Test void fallbackIsFinalEvenIfOperatorReturns(){var t=upload();service.heartbeat("operator-1",true);assertEquals("fallback",service.get(t.id()).status());}
    @Test void requestIdMakesUploadRetryIdempotent(){var u=new RatingService.Upload("request-123","描述",png);var a=service.create(u);var b=service.create(u);assertEquals(a.id(),b.id());assertEquals(409,assertThrows(Problem.class,()->service.create(new RatingService.Upload("request-123","不同描述",png))).status);}
    @Test void restartRetainsRatingButDropsOnlineLease(){service.heartbeat("operator-1",true);var t=upload();var restarted=new RatingService(db,60,clock);assertEquals("fallback",restarted.get(t.id()).status());assertArrayEquals(png,restarted.photo(t.id()).bytes());}
    @Test void invalidScoreAndImagesAreRejected(){service.heartbeat("operator-1",true);var t=upload();for(double score:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,101})assertThrows(Problem.class,()->service.rate(t.id(),new RatingService.Rating(score,"")));assertThrows(Problem.class,()->service.create(new RatingService.Upload("request-123","",new byte[]{1,2,3})));assertThrows(Problem.class,()->service.create(new RatingService.Upload("request-123","",new byte[8*1024*1024+1])));}
    @Test void concurrentManualSubmissionsHaveOneWinner()throws Exception{service.heartbeat("operator-1",true);var t=upload();ExecutorService pool=Executors.newFixedThreadPool(2);try{Callable<Integer> submit=()->{try{service.rate(t.id(),new RatingService.Rating(80.0,""));return 200;}catch(Problem p){return p.status;}};var results=pool.invokeAll(List.of(submit,submit));var codes=List.of(results.get(0).get(),results.get(1).get());assertTrue(codes.contains(200));assertTrue(codes.contains(409));}finally{pool.shutdownNow();}}
}
