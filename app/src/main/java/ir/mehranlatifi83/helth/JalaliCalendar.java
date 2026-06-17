package ir.mehranlatifi83.helth;

public class JalaliCalendar {

    private static final int[] G_DAYS_IN_MONTH = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    private static final int[] J_DAYS_IN_MONTH = {31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29};

    public static int[] toJalali(int gy, int gm, int gd) {
        int gY = gy - 1600;
        int gM = gm - 1;
        int gD = gd - 1;

        int gDayNo = 365 * gY
                + (gY + 3) / 4
                - (gY + 99) / 100
                + (gY + 399) / 400;

        for (int i = 0; i < gM; i++) gDayNo += G_DAYS_IN_MONTH[i];
        if (gM > 1 && isGregorianLeap(gy)) gDayNo++;
        gDayNo += gD;

        int jDayNo = gDayNo - 79;
        int jNp = jDayNo / 12053;
        jDayNo %= 12053;

        int jy = 979 + 33 * jNp + 4 * (jDayNo / 1461);
        jDayNo %= 1461;

        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365;
            jDayNo = (jDayNo - 1) % 365;
        }

        int jm = 0;
        for (; jm < 11 && jDayNo >= J_DAYS_IN_MONTH[jm]; jm++) {
            jDayNo -= J_DAYS_IN_MONTH[jm];
        }

        return new int[]{jy, jm + 1, jDayNo + 1};
    }

    private static boolean isGregorianLeap(int year) {
        return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
    }
}
