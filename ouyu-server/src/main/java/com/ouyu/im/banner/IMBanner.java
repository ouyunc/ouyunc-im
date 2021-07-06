package com.ouyu.im.banner;

import java.io.PrintStream;

/**
 * @Author fangzhenxun
 * @Description: IM banner
 * @Version V1.0
 **/
public class IMBanner {

    private static final String BANNER = "   ____    _    _  __     __  _    _            _____   __  __ \n" +
            "  / __ \\  | |  | | \\ \\   / / | |  | |          |_   _| |  \\/  |\n" +
            " | |  | | | |  | |  \\ \\_/ /  | |  | |  ______    | |   | \\  / |\n" +
            " | |  | | | |  | |   \\   /   | |  | | |______|   | |   | |\\/| |\n" +
            " | |__| | | |__| |    | |    | |__| |           _| |_  | |  | |\n" +
            "  \\____/   \\____/     |_|     \\____/           |_____| |_|  |_|";

    private static final String VERSION = "OUYU-IM::v1.0-SNAPSHOT";

    public static void printBanner(PrintStream printStream) {
        printStream.println(BANNER);
        printStream.println(VERSION);
    }

}
