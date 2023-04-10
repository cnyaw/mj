
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

  FileWriter log;                       // Log file.

  MahjongLog() throws Exception {

    //
    // Create log file.
    //

    Calendar cal = Calendar.getInstance();

    File dirLog = new File(LOG_PATH);
    if (!dirLog.exists()) {
      dirLog.mkdir();
    }

    log = new FileWriter(
                LOG_PATH + cal.get(Calendar.YEAR) +
                "-" + (1 + cal.get(Calendar.MONTH)) +
                "-" + cal.get(Calendar.DAY_OF_MONTH) +
                "-MjSvr.log",
                true);
  }

  //
  // Output message on the screen and write to log file.
  //

  void PrintLog(String mess) throws IOException {
    String head = (new Date()).toString() + ", ";
    System.out.print(head + mess);
    log.write(head + mess);
    log.flush();
  }
} // MahjongLog

// end of MahjongLog.java
