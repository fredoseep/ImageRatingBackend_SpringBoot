package com.example.rating;
public class Problem extends RuntimeException {
    public final int status;
    public Problem(int status,String message) {super(message);this.status=status;}
}
