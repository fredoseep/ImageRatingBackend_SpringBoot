package com.example.rating;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component
public class AccessFilter extends OncePerRequestFilter {
    private final String admin;
    public AccessFilter(@Value("${rating.admin-key:}") String admin)throws IOException{
        Path dir=Path.of("data");Files.createDirectories(dir);Path file=dir.resolve("keys.properties");Properties keys=new Properties();
        if(Files.exists(file)){try(var in=Files.newInputStream(file)){keys.load(in);}}
        if(!keys.containsKey("admin"))keys.setProperty("admin",generate());
        keys.remove("client");
        try(var out=Files.newOutputStream(file)){keys.store(out,"LAN lab keys - do not share admin key");}
        try{Files.setPosixFilePermissions(file,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));}catch(UnsupportedOperationException ignored){}
        this.admin=admin.isBlank()?keys.getProperty("admin"):admin;
        System.out.println("\n电脑评分页: http://127.0.0.1:8080/ （修改端口后请使用实际端口）\n管理员密钥: "+this.admin+"\n");
    }
    private static String generate(){byte[] b=new byte[24];new SecureRandom().nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    private void fail(HttpServletResponse r,int status,String message)throws IOException{r.setStatus(status);r.setContentType("application/json;charset=UTF-8");r.getWriter().write("{\"error\":\""+message+"\"}");}
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
        res.setHeader("Cache-Control","no-store");res.setHeader("X-Content-Type-Options","nosniff");res.setHeader("X-Frame-Options","DENY");res.setHeader("Referrer-Policy","no-referrer");
        String path=req.getRequestURI();
        if(path.startsWith("/api/")){
            if(path.startsWith("/api/admin/")){
                String actual=Objects.toString(req.getHeader("Authorization"),""),expected="Bearer "+admin;
                if(!MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8),expected.getBytes(StandardCharsets.UTF_8))){fail(res,401,"管理员密钥错误");return;}
            }
            if(req.getMethod().equals("POST")){
                long len=req.getContentLengthLong();if(len<=0||len>12*1024*1024){fail(res,413,"请求体为空、过大或未提供Content-Length");return;}
                if(req.getContentType()==null||!req.getContentType().toLowerCase(Locale.ROOT).startsWith("application/json")){fail(res,415,"需要application/json");return;}
            }
        }
        chain.doFilter(req,res);
    }
}
