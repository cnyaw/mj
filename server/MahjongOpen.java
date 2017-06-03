import java.util.Random;

public class MahjongOpen
{
  boolean windOpen;                     //是否開新風局
  int master;                           //莊家
  int oldMaster;                        //舊莊家
  int winner;                           //贏家編號
  int masterCount;                      //連莊計數
  int wind;                             //風代表
  int flower;                           //花代表
  int gameCount;                        //非流局計數

  Random rand;

  MahjongOpen()
  {
    rand = new Random();
    rand.setSeed(System.currentTimeMillis());
    init();
  }

  public void init()
  {
    windOpen=true;                      //風局開始
    wind=0;                             //從東風開始
    gameCount=0;                        //遊戲計數
  }

  public  int getRand(int i)
  {
    if (i==0)
      return 0;
    return Math.abs(rand.nextInt() % i);
  }

  // 新風局開始,重新抓位
  public int newWind()
  {
    int master=0;

    master=getRand(4);

    return master;
  }

  // 設定莊家
  public int getMaster()
  {
    //是否開新風局
    if(windOpen)
    {
      oldMaster=-1;
      winner=-1;
      masterCount=0;
      //找出莊家
      master=newWind();
      windOpen=false;
      return master;
    }

    //儲存舊莊家
    oldMaster=master;
    //是否連莊
    if(winner==-1 || winner==master)
      masterCount++;
    else
    {
      //風局+1
      if((++gameCount)%4==0)
      {
        gameCount=0;
        ++wind;
      }

      masterCount=0;
      //輪下一位做莊
      if(++master>=4)
        master=0;
    }
    return master;
  }
}
