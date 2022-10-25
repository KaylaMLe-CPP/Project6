import java.io.FileInputStream;
import java.io.*;
import java.lang.Integer;
import java.util.*;

public class parser {
    static Map<String, String> compField = new HashMap<>();
    static Map<String, String> jumpField = new HashMap<>();

    public static void main(String args[]) {
        // populates compField hashmap with corresponding values a and c1-c6
        compField.put("0", "0101010");// janky but quick lookup
        compField.put("1", "0111111");
        compField.put("-1", "0111010");
        compField.put("D", "0001100");
        compField.put("A", "0110000");
        compField.put("M", "1110000");
        compField.put("!D", "0001101");
        compField.put("!A", "0110001");
        compField.put("!M", "1110001");
        compField.put("-D", "0001111");
        compField.put("-A", "0110011");
        compField.put("-M", "1110011");
        compField.put("D+1", "0011111");
        compField.put("A+1", "0110111");
        compField.put("M+1", "1110111");
        compField.put("D-1", "0001110");
        compField.put("A-1", "0110010");
        compField.put("M-1", "1110010");
        compField.put("D+A", "0000010");
        compField.put("D+M", "1000010");
        compField.put("D-A", "0010011");
        compField.put("D-M", "1010011");
        compField.put("A-D", "0000111");
        compField.put("M-D", "1000111");
        compField.put("D&A", "0000000");
        compField.put("D&M", "1000000");
        compField.put("D|A", "0010101");
        compField.put("D|M", "1010101");

        // I know there's a patter to the bits in the j field
        // but the pattern breaks down for JNE and JMP
        // this could be optimized if I had time
        jumpField.put("JGT", "001");
        jumpField.put("JEQ", "010");
        jumpField.put("JGE", "011");
        jumpField.put("JLT", "100");
        jumpField.put("JNE", "101");
        jumpField.put("JLE", "110");
        jumpField.put("JMP", "111");

        Map<String, String> labels = new HashMap<>();

        File srcScript = new File(args[0]);

        FileInputStream readSrc;
        try {
            readSrc = new FileInputStream(srcScript);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        ArrayList<String> lines = fileToStrArr(readSrc);
        String[] parsed = new String[lines.size()];

        int pos = 0;
        boolean labelFound = false;

        for (int i = 0; i < lines.size(); i++) {
            String thisLine = lines.get(i).trim();
            int lineLimit = thisLine.indexOf(" ") == -1 ? thisLine.length() : thisLine.indexOf(" ");
            thisLine = thisLine.substring(0, lineLimit);

            // comments and emptpy lines
            if (thisLine.startsWith("//") || thisLine.length() == 0) {
                continue;
                // label
            } else if (thisLine.startsWith("(")) {
                labelFound = true;
                labels.put(thisLine.substring(1, thisLine.length() - 1), getLabelVal(pos));

                continue;
                // A instruction
            } else if (thisLine.startsWith("@")) {
                labelFound = true;

                String valSubstr = thisLine.substring(1);
                parsed[pos] = isNum(valSubstr) ? ("0" + getLabelVal(Integer.parseInt(valSubstr))) : thisLine;
            } else {// C instruction
                String compKey;
                String destField = "null";
                int separator;

                // c field
                if (thisLine.contains("=")) {
                    separator = thisLine.indexOf("=");
                    compKey = thisLine.substring(separator + 1);
                    destField = thisLine.substring(0, separator);
                } else {
                    separator = thisLine.indexOf(";");
                    compKey = thisLine.substring(0, separator);
                }

                parsed[pos] = "111" + compField.get(compKey);

                // d and j fields
                if (destField == "null") {
                    parsed[pos] += "000" + jumpField.get(thisLine.substring(separator + 1, thisLine.length()));
                } else {
                    // if these were char, + to concatenate would not work (integer adds ascii
                    // values?)
                    String aChar = "0";
                    String mChar = "0";
                    String dChar = "0";

                    if (destField.contains("A")) {
                        aChar = "1";
                    }
                    if (destField.contains("M")) {
                        mChar = "1";
                    }
                    if (destField.contains("D")) {
                        dChar = "1";
                    }

                    parsed[pos] += aChar + dChar + mChar + "000";
                }
            }

            pos++;
        }

        // second pass to properly parse labels with symbols
        String[] addrSymbols = new String[1024];
        int symbolIndex = 0;

        if (labelFound) {
            for (int i = 0; i < parsed.length; i++) {
                if (parsed[i] == null) {
                    break;
                }

                if (parsed[i].startsWith("@")) {
                    String symbolLabel = parsed[i].substring(1);
                    String labelVal = labels.get(symbolLabel);
                    parsed[i] = "0";

                    // symbol label
                    if (labelVal != null) {
                        parsed[i] += labelVal;
                        // previously seen mem address label
                    } else if (Arrays.asList(addrSymbols).contains(symbolLabel)) {
                        parsed[i] += getLabelVal(Arrays.asList(addrSymbols).indexOf(symbolLabel));
                        // new mem address label
                    } else {
                        parsed[i] += getLabelVal(symbolIndex);
                        addrSymbols[symbolIndex] = symbolLabel;
                        symbolIndex++;
                    }
                }
            }
        }

        String fileName = args[0].substring(0, args[0].indexOf(".")) + ".hack";
        File bin = new File(fileName);

        try {
            bin.createNewFile();

            FileWriter binWriter = new FileWriter(fileName);
            for (int i = 0; i < parsed.length; i++) {
                if (parsed[i] == null) {
                    break;
                }
                binWriter.write(parsed[i] + '\n');
            }

            binWriter.close();
            readSrc.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return;
    }

    private static ArrayList<String> fileToStrArr(FileInputStream src) {
        ArrayList<String> parsed = new ArrayList<String>();
        parsed.add("");
        int currentLine = 0;
        int nextByte = readByte(src);

        while (nextByte != -1) {
            // if at end of line, and a new
            if ((char) nextByte == '\n') {
                currentLine++;
                parsed.add("");
            } else {
                parsed.set(currentLine, parsed.get(currentLine) + (char) nextByte);
            }
            nextByte = readByte(src);
        }

        return parsed;
    }

    // FileInputStream .read() with error handling
    private static int readByte(FileInputStream src) {
        int thisByte = -1;

        try {
            thisByte = src.read();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return thisByte;
    }

    private static String fillStrWithZeroes(String orig, int length) {
        while (orig.length() < length) {
            orig = '0' + orig;
        }

        return orig;
    }

    private static String getLabelVal(int position) {
        return fillStrWithZeroes(Integer.toBinaryString(position), 15);
    }

    private static boolean isNum(String str) {
        int newLen = str.replaceAll("^\\d+$", "").length();
        return newLen == 0;
    }
}
