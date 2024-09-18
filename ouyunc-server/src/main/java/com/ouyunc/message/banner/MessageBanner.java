package com.ouyunc.message.banner;

import java.io.PrintStream;

/**
 * @Author fzx
 * @Description:  banner
 **/
public class MessageBanner {

    private static final String BANNER =
            "  ___  _   _ _   _ _   _ _ __   ___        _ __ ___   ___  ___ ___  __ _  __ _  ___ \n" +
            " / _ \\| | | | | | | | | | '_ \\ / __|      | '_ ` _ \\ / _ \\/ __/ __|/ _` |/ _` |/ _ \\\n" +
            "| (_) | |_| | |_| | |_| | | | | (__       | | | | | |  __/\\__ \\__ \\ (_| | (_| |  __/\n" +
            " \\___/ \\__,_|\\__, |\\__,_|_| |_|\\___|      |_| |_| |_|\\___||___/___/\\__,_|\\__, |\\___|\n" +
            "              __/ |                                                       __/ |     \n" +
            "             |___/                                                       |___/      ";


    private static final String VERSION = "\n OUYUNC-MESSAGE::v6.0.0 \n";

    public static void printBanner(PrintStream printStream) {
        printStream.println(BANNER);
        printStream.println(VERSION);
    }

}
