package com.example.rating;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RatingService {
    public static final int MAX_IMAGE=8*1024*1024;
    private final JdbcTemplate db;
    private final int waitSeconds;
    private final Clock clock;
    private static final String COLUMNS="id,description,mime,status,reason,created,deadline,score,comment,completed";
    private final Map<String,Double> operators=new HashMap<>();
    public record Upload(String request_id,String description,byte[] image_base64) {}
    public record Rating(Double score,String comment) {}
    public record Task(String id,String description,String mime,String status,String reason,double created,double deadline,Double score,String comment,Double completed) {}
    public record Photo(byte[] bytes,String mime) {}
    @org.springframework.beans.factory.annotation.Autowired
    public RatingService(JdbcTemplate db,@Value("${rating.wait-seconds:60}") int seconds) {this(db,seconds,Clock.systemUTC());}
    RatingService(JdbcTemplate db,int seconds,Clock clock) {
        if(seconds<5||seconds>3600)throw new IllegalArgumentException("人工等待秒数需为5~3600");
        this.db=db;this.waitSeconds=seconds;this.clock=clock;
    }
    private double now(){return clock.millis()/1000.0;}
    private boolean online(){double n=now();operators.values().removeIf(t->n-t>=15);return !operators.isEmpty();}
    public synchronized Map<String,Object> heartbeat(String session,boolean active){
        if(session==null||session.length()<8||session.length()>100)throw new Problem(400,"心跳session无效");
        sweep();
        if(active)operators.put(session,now());else operators.remove(session);
        return Map.of("online",online(),"heartbeat_ttl",15);
    }
    private void sweep(){
        db.update("UPDATE tasks SET status='fallback',reason='human_timeout',completed=? WHERE status='pending' AND deadline<=?",now(),now());
        if(!online())db.update("UPDATE tasks SET status='fallback',reason='operator_offline',completed=? WHERE status='pending'",now());
    }
    private Task task(java.sql.ResultSet r,int i)throws java.sql.SQLException{
        return new Task(r.getString("id"),r.getString("description"),r.getString("mime"),r.getString("status"),r.getString("reason"),r.getDouble("created"),r.getDouble("deadline"),(Double)r.getObject("score"),r.getString("comment"),(Double)r.getObject("completed"));
    }
    public synchronized Task create(Upload u){
        if(u.request_id()==null||u.request_id().length()<8||u.request_id().length()>100)throw new Problem(400,"request_id长度需为8~100");
        String description=u.description()==null?"":u.description();
        if(description.length()>4000)throw new Problem(400,"图片描述最多4000字");
        byte[] image=u.image_base64();
        if(image==null||image.length==0||image.length>MAX_IMAGE)throw new Problem(413,"图片需为1字节~8MiB");
        String mime=detectMime(image),digest;
        try{MessageDigest md=MessageDigest.getInstance("SHA-256");md.update(image);md.update((byte)0);digest=HexFormat.of().formatHex(md.digest(description.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException(e);}
        sweep();
        var rows=db.queryForList("SELECT id,fingerprint FROM tasks WHERE request_id=?",u.request_id());
        if(!rows.isEmpty()){
            if(!digest.equals(rows.get(0).get("FINGERPRINT")))throw new Problem(409,"request_id不能用于不同内容");
            return get((String)rows.get(0).get("ID"));
        }
        String id=UUID.randomUUID().toString();double n=now();boolean on=online();
        db.update("INSERT INTO tasks(id,request_id,fingerprint,description,mime,image,status,reason,created,deadline,completed) VALUES(?,?,?,?,?,?,?,?,?,?,?)",id,u.request_id(),digest,description,mime,image,on?"pending":"fallback",on?null:"operator_offline",n,n+waitSeconds,on?null:n);
        return get(id);
    }
    static String detectMime(byte[] b){
        if(b.length>=3&&(b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255)return "image/jpeg";
        if(b.length>=8&&Arrays.equals(Arrays.copyOf(b,8),new byte[]{(byte)137,80,78,71,13,10,26,10}))return "image/png";
        if(b.length>=6){String s=new String(b,0,6,StandardCharsets.US_ASCII);if(s.equals("GIF87a")||s.equals("GIF89a"))return "image/gif";}
        if(b.length>=12&&new String(b,0,4,StandardCharsets.US_ASCII).equals("RIFF")&&new String(b,8,4,StandardCharsets.US_ASCII).equals("WEBP"))return "image/webp";
        throw new Problem(415,"仅支持JPEG、PNG、GIF、WebP（按文件头检查）");
    }
    public synchronized Task get(String id){sweep();var rows=db.query("SELECT "+COLUMNS+" FROM tasks WHERE id=?",this::task,id);if(rows.isEmpty())throw new Problem(404,"任务不存在");return rows.get(0);}
    public synchronized Photo photo(String id){var rows=db.query("SELECT image,mime FROM tasks WHERE id=?",(r,i)->new Photo(r.getBytes(1),r.getString(2)),id);if(rows.isEmpty())throw new Problem(404,"任务不存在");return rows.get(0);}
    public synchronized Map<String,Object> listing(){sweep();return Map.of("online",online(),"now",now(),"tasks",db.query("SELECT "+COLUMNS+" FROM tasks ORDER BY created DESC LIMIT 100",this::task));}
    public synchronized Task rate(String id,Rating rating){
        Double s=rating.score();String c=rating.comment()==null?"":rating.comment();
        if(s==null||!Double.isFinite(s)||s<0||s>100)throw new Problem(400,"评分需为0~100的有限数字");
        if(c.length()>4000)throw new Problem(400,"评语最多4000字");
        Task t=get(id);if(!t.status().equals("pending"))throw new Problem(409,"任务已完成或已回退，不能再次人工评分");
        db.update("UPDATE tasks SET status='rated',score=?,comment=?,completed=? WHERE id=? AND status='pending'",s,c,now(),id);
        return get(id);
    }
}
