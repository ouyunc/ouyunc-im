package com.ouyunc.im.resolver;

/**
 * 命令行参数解析器
 */
public class CommandLineArgsPropertyResolver extends AbstractPropertyResolver {


    /**
     * 解析参数封装到命令行参数
     *
     * @param args
     * @return
     */
    @Override
    public CommandLineArgs resolverArgs(String... args) {
        CommandLineArgs commandLineArgs = new CommandLineArgs();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                String optionText = arg.substring(2);
                String optionName;
                String optionValue = null;
                int indexOfEqualsSign = optionText.indexOf('=');
                if (indexOfEqualsSign > -1) {
                    optionName = optionText.substring(0, indexOfEqualsSign);
                    optionValue = optionText.substring(indexOfEqualsSign + 1);
                } else {
                    optionName = optionText;
                }
                if (optionName.isEmpty()) {
                    throw new IllegalArgumentException("Invalid argument syntax: " + arg);
                }
                commandLineArgs.addOptionArg(optionName, optionValue);
            } else {
                commandLineArgs.addNonOptionArg(arg);
            }
        }
        return commandLineArgs;

    }
}
