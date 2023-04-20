//
// mjclient.js
// Mahjong WebSocket client.
//
// 2023/4/19 Waync Cheng.
//

function MahjongClient(addr, token) {
  var mj = this;
  this.myId = -1;
  this.myGameId = -1;
  this.myPos = -1;
  this.pCard = null;
  this.oCard = null;                    // Player cards, player open cards.
  this.cDrop = null;                    // Drop cards.
  this.pick = -1;
  this.posPick = -1;                    // Current pick card, current pick player pos.
  // Event callback.
  this.onconnect = null;
  this.ondisconnect = null;
  this.onlogin = null;
  this.onclose = null;
  this.onerror = null;
  this.onaddplayer = null;
  this.onremoveplayer = null;
  this.onsetplayername = null;
  this.onplayerpass = null;
  this.onplayertin = null;
  this.onplayerlon = null;
  this.onaddgame = null;
  this.onremovegame = null;
  this.onjoingame = null;
  this.onleavegame = null;
  this.ongamestart = null;
  this.ongameroundstart = null;
  this.ongameroundend = null;
  this.ongameend = null;
  this.ongameupdate = null;

  var ws = new WebSocket(addr, "mj");
  ws.binaryType = "arraybuffer";

  var tHeartBeat;

  ws.onopen = function(evt) {
    send(ws, token);
    tHeartBeat = setInterval(function(){ send(ws, '11'); }, 8000);
    if (mj.onconnect) {
      mj.onconnect();
    }
  }

  ws.onclose = function(evt) {
    clearInterval(tHeartBeat)
    if (mj.ondisconnect) {
      mj.ondisconnect();
    }
  }

  ws.onmessage = function(evt) {
    if (evt.data instanceof ArrayBuffer) {
      var b = new Uint8Array(evt.data);
      var s = '';
      for (var i = 1; i < b.length - 1; i++) {
        s = s + String.fromCharCode(b[i]);
      }

      mj.posPick = -1;
      var cmd = s.split(',').map(Number); // Make sure convert to numbers.
      switch (cmd[0] & 0xff)
      {
      case 0xa:                         // Server is ready.
        mj.myId = cmd[1];
        if (mj.onlogin) {
          mj.onlogin(cmd[1]);
        }
        break;
      case 0x10:                        // New player.
        if (mj.onaddplayer) {
          var scmd = s.split(',');
          mj.onaddplayer(scmd[1], decodeURIComponent(scmd[2]));
        }
        break;
      case 0x11:                        // Player leave.
        if (mj.onremoveplayer) {
          mj.onremoveplayer(cmd[1]);
        }
        break;
      case 0x20:                        // New game.
        if (mj.onaddgame) {
          mj.onaddgame(cmd[1], cmd[2], cmd[3], cmd[4], cmd[5], 0 != cmd[6]);
        }
        break;
      case 0x22:                        // Leave game.
        if (-1 == cmd[2]) {
          if (mj.onremovegame) {
            mj.onremovegame(cmd[1]);
          }
        } else {
          if (mj.onleavegame) {
            mj.onleavegame(cmd[2], cmd[1]);
          }
        }
        break;
      case 0x23:                        // Join game.
        if (mj.myId == cmd[2]) {
          mj.myGameId = cmd[1];
          mj.myPos = cmd[3];
        }
        if (mj.onjoingame) {
          mj.onjoingame(cmd[2], cmd[1], cmd[3]);
        }
        break;
      case 0x24:                        // Game is busy.
        if (mj.onerror) {
          mj.onerror('Game is busy');
        }
        break;
      case 0x25:                        // Game start.
        if (mj.ongamestart) {
          mj.ongamestart(cmd[1]);
        }
        break;
      case 0x32:                        // Game init.
        mj.cDrop = [];
        mj.pCard = [];
        mj.oCard = [];
        for (var i = 0; i < 4; i++) {
          mj.oCard[i] = [];
          mj.pCard[i] = [];
          for (var j = 0; j < 16; j++) {
            mj.pCard[i][j] = 0;
          }
        }
        for (var i = 7; i < cmd.length; i++) {
          mj.pCard[mj.myPos][i - 7] = cmd[i];
        }
        if (mj.ongameroundstart) {
          mj.ongameroundstart(cmd[1], cmd[2], cmd[3], cmd[4], cmd[5], cmd[6]);
        }
        renderGame();
        break;
      case 0x33:                        // Game ends.
        if (mj.ongameend) {
          mj.ongameend();
        }
        break;
      case 0x34:                        // Game action.
        var pos = cmd[1];
        switch (cmd[2] & 0xff)
        {
        case 1:                         // Pass.
          if (mj.onplayerpass) {
            mj.onplayerpass(pos);
          }
          break;
        case 2:                         // Exchange.
          mj.cDrop.push(cmd[3]);
          renderGame();
          break;
        case 3:                         // Chi.
          var card = cmd[3];
          mj.oCard[pos].push(card, card + 1, card + 2);
          mj.pCard[pos].splice(0, 3);
          if (pos != mj.myPos) {
            mj.posPick = pos;
            mj.pick = 0;
          }
          mj.cDrop.pop();
          renderGame();
          break;
        case 4:                         // Pon.
          var card = cmd[3];
          mj.oCard[pos].push(card, card, card);
          mj.pCard[pos].splice(0, 3);
          if (pos != mj.myPos) {
            mj.posPick = pos;
            mj.pick = 0;
          }
          mj.cDrop.pop();
          renderGame();
          break;
        case 5:                         // Gun.
          var card = cmd[3];
          mj.oCard[pos].push(card, card, card, card);
          mj.pCard[pos].splice(0, 3);
          if (pos != mj.myPos) {
            mj.posPick = pos;
            mj.pick = 0;
          }
          mj.cDrop.pop();
          renderGame();
          break;
        case 6:                         // Lon.
          mj.posPick = pos;
          mj.pick = cmd[3];
          renderGame();
          if (mj.onplayerlon) {
            var lon = [];
            for (var i = 4; i < cmd.length; i++) {
              lon.push(cmd[i]);
            }
            mj.onplayerlon(pos, lon);
          }
          break;
        case 7:                         // Pick.
          mj.posPick = pos;
          mj.pick = cmd[3];
          renderGame();
          break;
        case 8:                         // State.
          var state = cmd[3] & 0xff;
          var idx = 4;
          var s2 = '';
          if (0 != (state & (1 << 2))) { // Can tin.
            var count = cmd[idx];
            idx = idx + 1;
            for (var i = idx; i < cmd.length; i++) {
              s2 = s2 + cmd[i] + ',';
            }
          }
          if (0 != (state & (1 << 4))) { // Can gun.
            var count = cmd[idx];
            idx = idx + 1;
            for (var i = idx; i < cmd.length; i++) {
              s2 = s2 + cmd[i] + ',';
            }
          }
          if (0 != (state & (1 << 5))) { // Can pon.
            var count = cmd[idx];
            idx = idx + 1;
          }
          if (0 != (state & (1 << 6))) { // Can chi.
            var count = cmd[idx];
            idx = idx + 1;
            for (var i = idx; i < cmd.length; i++) {
              s2 = s2 + cmd[i] + ',';
            }
          }
          if (0 != (state & (1 << 3))) { // Can lon.
          }
          break;
        case 9:                         // Round end.
          if (mj.ongameroundend) {
            mj.ongameroundend();
          }
          break;
        case 10:                        // Show cards.
          var idx = 3;
          for (var i = 0; i < 3; i++) {
            var pos = cmd[idx];
            var count = cmd[idx + 1];
            var offset = idx + 2 + count - mj.pCard[pos].length;
            for (var j = 0; j < mj.pCard[pos].length; j++) {
              var card = cmd[offset + j];
              mj.pCard[pos][j] = card;
            }
            idx = idx + 2 + count;
          }
          renderGame();
          break;
        case 11:                        // Tin.
          if (mj.onplayertin) {
            mj.onplayertin(pos, cmd[3]);
          }
          renderGame();
          break;
        case 12:                        // Patch.
          for (var i = 3; i < cmd.length; i += 2) {
            var c = cmd[i];
            mj.oCard[pos].push(c);
            var c2 = cmd[i + 1];
            if (0 != c2) {
              var idx = mj.pCard[pos].indexOf(c);
              if (-1 != idx) {
                mj.pCard[pos][idx] = c2;
              }
            }
          }
          if (pos == mj.myPos) {
            mj.pCard[pos].sort(sortNumber); // Make sure sort by numbers.
          }
          renderGame();
          break;
        }
        break;
      case 0x36:                        // Set player name.
        if (mj.onsetplayername) {
          var scmd = s.split(',');
          mj.onsetplayername(scmd[1], decodeURIComponent(scmd[2]));
        }
        break;
      }
    }
  }

  ws.onerror = function(evt) {
    clearInterval(tHeartBeat)
    if (mj.onerror) {
      mj.onerror(evt.data);
    }
  }

  function sortNumber(a, b) {
    return a - b;
  }

  function str2ab(str) {
    var arr = [];
    for(var i = 0; i < str.length; i++) {
      arr.push(str.charCodeAt(i));
    }
    return new Uint8Array(arr);
  }

  function send(ws, str) {
    ws.send(str2ab(str));
  }

  function renderGame() {
    if (mj.ongameupdate) {
      mj.ongameupdate();
    }
  }

  this.changeName = function(name) {
    send(ws, '54,' + name);
  }

  this.joinGame = function(game) {
    send(ws, '35,' + game);
  }
}