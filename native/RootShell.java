package com.launchertop.aranha;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class RootShell {
    private static Process proc;
    private static OutputStream out;

    /** Manda um comando para um shell root que fica aberto (rapido, sem esperar resposta). */
    public static synchronized void send(String cmd) {
        try {
            if (proc == null) {
                proc = new ProcessBuilder("su").redirectErrorStream(true).start();
                out = proc.getOutputStream();
                final InputStream in = proc.getInputStream();
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            byte[] b = new byte[1024];
                            while (in.read(b) >= 0) { }
                        } catch (Exception e) { }
                    }
                }).start();
            }
            out.write((cmd + "\n").getBytes());
            out.flush();
        } catch (Exception e) {
            proc = null;
        }
    }

    /** Roda um comando root e devolve a saida. */
    public static String run(String cmd) {
        try {
            Process p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            p.waitFor();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Roda um comando root e diz se terminou com sucesso. */
    public static boolean ok(String cmd) {
        try {
            Process p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
            InputStream in = p.getInputStream();
            byte[] b = new byte[1024];
            while (in.read(b) >= 0) { }
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
