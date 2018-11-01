package com.example.vera.torandroidtest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

/**
 * Created by vera on 18-10-31.
 */

public class processBuilderTest {
    public static void main(String[] args) throws InterruptedException, IOException{
        ProcessBuilder pb = new ProcessBuilder("echo", "This is ProcessBuilder Example");
        Process process = pb.start();
        int errCode = process.waitFor();
        System.out.println("Error Output: " + errCode);
        System.out.println("Process Output: " + convert(process.getInputStream()));
    }
    public static String convert(InputStream inputStream){
        Scanner scanner = new Scanner(inputStream);
        return scanner.useDelimiter("\\A").next();
    }

}
