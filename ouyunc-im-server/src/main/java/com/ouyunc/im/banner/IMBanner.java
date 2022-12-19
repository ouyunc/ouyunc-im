package com.ouyunc.im.banner;

import java.io.PrintStream;

/**
 * @Author fangzhenxun
 * @Description: IM banner
 * @Version V3.0
 **/
public class IMBanner {

    private static final String BANNER = "\n" +
            "  ______    __    __  ____    ____  __    __  .__   __.   ______         __  .___  ___. \n" +
            " /  __  \\  |  |  |  | \\   \\  /   / |  |  |  | |  \\ |  |  /      |       |  | |   \\/   | \n" +
            "|  |  |  | |  |  |  |  \\   \\/   /  |  |  |  | |   \\|  | |  ,----' ______|  | |  \\  /  | \n" +
            "|  |  |  | |  |  |  |   \\_    _/   |  |  |  | |  . `  | |  |     |______|  | |  |\\/|  | \n" +
            "|  `--'  | |  `--'  |     |  |     |  `--'  | |  |\\   | |  `----.       |  | |  |  |  | \n" +
            " \\______/   \\______/      |__|      \\______/  |__| \\__|  \\______|       |__| |__|  |__| \n";


    private static final String VERSION = "OUYUNC-IM::v3.0 \n";

    public static void printBanner(PrintStream printStream) {
        printStream.print(BANNER);
        printStream.println(VERSION);
    }

}
