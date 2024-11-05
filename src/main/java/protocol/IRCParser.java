package protocol;

public class IRCParser {
    public static IRCMessage parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }

        String[] parts = raw.split(" ");
        int index = 0;

        IRCMessage message = new IRCMessage("");

        // prefix 처리
        if (parts[0].startsWith(":")) {
            message.setPrefix(parts[0].substring(1));
            index++;
        }

        // command 처리
        if (index < parts.length) {
            message.setCommand(parts[index++]);
        }

        // params와 text 처리
        String[] params = new String[parts.length - index];
        int paramIndex = 0;

        for (int i = index; i < parts.length; i++) {
            if (parts[i].startsWith(":")) {
                // 나머지를 text로 처리
                StringBuilder text = new StringBuilder(parts[i].substring(1));
                for (int j = i + 1; j < parts.length; j++) {
                    text.append(" ").append(parts[j]);
                }
                message.setText(text.toString());
                break;
            } else {
                params[paramIndex++] = parts[i];
            }
        }

        if (paramIndex > 0) {
            String[] finalParams = new String[paramIndex];
            System.arraycopy(params, 0, finalParams, 0, paramIndex);
            message.setParams(finalParams);
        }

        return message;
    }
}