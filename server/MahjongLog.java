
//
// MahjongLog.java, 麻將 log file support.
// 2023/4/10 Waync Cheng
//

import java.io.*;
import java.util.*;

//
// Mahjong log file support.
//

public class MahjongLog {

  static final String LOG_PATH = "./log/";

  String currDate;                      // Saved current date for latest log file name.
  FileWriter log;                       // Log file.

  MahjongLog() throws Exception {

    //
    // Create log file folder.
    //

    File dirLog = new File(LOG_PATH);
    if (!dirLog.exists()) {
      dirLog.mkdir();
    }
  }

  //
  // Output message on the screen and write to log file.
  //

  void PrintLog(String mess) throws IOException {
    checkLogFileDate();
    String head = (new Date()).toString() + ", ";
    System.out.print(head + mess);
    log.write(head + mess);
    log.flush();
  }

  void checkLogFileDate() throws IOException {
    String today = getDateString();
    if (today != currDate) {
      log = new FileWriter(LOG_PATH + today + "-MjSvr.log", true);
      currDate = today;
    }
  }

  String getDateString() {
    Calendar cal = Calendar.getInstance();
    String s = cal.get(Calendar.YEAR) + "-" +
               (1 + cal.get(Calendar.MONTH)) + "-" +
               cal.get(Calendar.DAY_OF_MONTH);
    return s;
  }

} // MahjongLog

// end of MahjongLog.java
