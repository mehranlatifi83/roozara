package ir.mehranlatifi83.helth;

public class JalaliCalendar {

    public static int[] toJalali(int gy, int gm, int gd) {
        int[] gDayNo = {0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

        int gy2 = (gm > 2) ? gy + 1 : gy;
        int gDayCount = 365 * (gy - 1)
                + (gy2 + 3) / 4
                - (gy2 + 99) / 100
                + (gy2 + 399) / 400;
        for (int i = 1; i < gm; i++) gDayCount += gDayNo[i];
        if (gm > 2 && isGregorianLeap(gy)) gDayCount++;
        gDayCount += gd - 1;

        int jDayNo = gDayCount - 79;
        int jNp = jDayNo / 12053;
        jDayNo %= 12053;

        int jy = 979 + 33 * jNp + 4 * (jDayNo / 1461);
        jDayNo %= 1461;

        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365;
            jDayNo = (jDayNo - 1) % 365;
        }

        int[] jDayNoArr = {31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29};
        int jm = 0;
        for (int i = 0; i < 11 && jDayNo >= jDayNoArr[i]; i++) {
            jDayNo -= jDayNoArr[i];
            jm++;
        }

        return new int[]{jy, jm + 1, jDayNo + 1};
    }

    private static boolean isGregorianLeap(int year) {
        return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
    }
}
