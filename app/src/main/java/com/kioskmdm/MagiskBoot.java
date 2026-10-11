package com.kioskmdm;

import java.io.OutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Optional, user-initiated Magisk boot recovery for this app's accessibility service. */
final class MagiskBoot {
    private static final String SCRIPT_PATH="/data/adb/service.d/kioskmdm-shade.sh";
    private static final String BOOT_SCRIPT=
        "#!/system/bin/sh\n" +
        "# Restore only the KioskMDM accessibility service after boot (Android 8.1).\n" +
        "COMPONENT=\"com.kioskmdm/.BackKeyService\"\n" +
        "while [ \"$(getprop sys.boot_completed)\" != \"1\" ]; do sleep 5; done\n" +
        "sleep 25\n" +
        "for pass in 1 2; do\n" +
        "  if pm path com.kioskmdm >/dev/null 2>&1; then\n" +
        "    current=\"$(settings --user 0 get secure enabled_accessibility_services 2>/dev/null)\"\n" +
        "    [ \"$current\" = \"null\" ] && current=\"\"\n" +
        "    oldIFS=\"$IFS\"; IFS=\":\"\n" +
        "    kept=\"\"\n" +
        "    for service in $current; do\n" +
        "      [ -z \"$service\" ] && continue\n" +
        "      [ \"$service\" = \"$COMPONENT\" ] && continue\n" +
        "      kept=\"${kept}${kept:+:}${service}\"\n" +
        "    done\n" +
        "    IFS=\"$oldIFS\"\n" +
        "    # Temporarily unbind only our service. Other accessibility services are retained.\n" +
        "    settings --user 0 put secure enabled_accessibility_services \"$kept\"\n" +
        "    sleep 2\n" +
        "    settings --user 0 put secure enabled_accessibility_services \"${kept}${kept:+:}${COMPONENT}\"\n" +
        "    settings --user 0 put secure accessibility_enabled 1\n" +
        "  fi\n" +
        "  sleep 40\n" +
        "done\n";

    static String execute(boolean install) {
        String command;
        if (install) {
            command = "set -eu\n"
                + "mkdir -p /data/adb/service.d\n"
                + "cat > " + SCRIPT_PATH + " <<'KIOSKMDM_SCRIPT_END'\n"
                + BOOT_SCRIPT
                + "KIOSKMDM_SCRIPT_END\n"
                + "chmod 700 " + SCRIPT_PATH + "\n"
                + "test -x " + SCRIPT_PATH + "\n"
                + "printf 'OK\\n'\n";
        } else {
            command = "set -eu\nrm -f " + SCRIPT_PATH + "\nprintf 'OK\\n'\n";
        }
        Process p=null;
        try {
            p=new ProcessBuilder("su","-c","sh").redirectErrorStream(true).start();
            try (OutputStream stream=p.getOutputStream()) {
                stream.write(command.getBytes(StandardCharsets.UTF_8));
                stream.flush();
            }
            boolean ended=p.waitFor(60,TimeUnit.SECONDS);
            if (!ended) {
                p.destroy();
                return "לא התקבלה תשובה מ-Magisk. בדוק האם הופיעה בקשת Root.";
            }
            byte[] data=new byte[2048];
            int n;
            try(InputStream in=p.getInputStream()){n=in.read(data);}
            String output=n>0?new String(data,0,n,StandardCharsets.UTF_8).trim():"";
            if (p.exitValue()==0 && output.contains("OK")) {
                return install ? "מנגנון Magisk הותקן. לאחר אתחול הווילון יופעל מחדש באופן אוטומטי."
                               : "מנגנון Magisk הוסר. הווילון הרגיל נשאר מותקן.";
            }
            return "Magisk לא ביצע את הפעולה: "+(output.length()>180?output.substring(0,180):output);
        } catch(Exception exception) {
            return "הרשאת Root לא התקבלה או Magisk אינו זמין.";
        } finally {
            if(p!=null)p.destroy();
        }
    }
}
