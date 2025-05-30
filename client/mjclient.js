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
  this.state = 0;
  this.tinCard = this.gunCard = this.ponCard = this.chiCard = []; // Can tin|gun|pon|chi cards list.
  this.canLon = false;                     // Can lon.
  this.KEY_PASS = 1000;
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
  this.onaddgame = null;
  this.onremovegame = null;
  this.onjoingame = null;
  this.onleavegame = null;
  this.ongamestart = null;
  this.ongameroundstart = null;
  this.ongameroundend = null;
  this.ongamestate = null;
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
        for (var i = 2; i <= 5; i++) {
          if (cmd[i] == mj.myId) {
            mj.myGameId = cmd[1];
            mj.myPos = i - 2;
            mj.state = 0;
            mj.tinCard = mj.gunCard = mj.ponCard = mj.chiCard = [];
            mj.canLon = false;
            break;
          }
        }
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
        if (cmd[2] == mj.myId || cmd[1] == mj.myGameId) {
          mj.myGameId = mj.myPos = -1;
          mj.state = 0;
        }
        break;
      case 0x23:                        // Join game.
        if (mj.myId == cmd[2]) {
          mj.myGameId = cmd[1];
          mj.myPos = cmd[3];
          mj.state = 0;
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
      case 0x32:                        // Round start.
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
        for (var i = 0; i < 16; i++) {
          mj.pCard[mj.myPos][i] = cmd[i + 7];
        }
        if (23 == cmd.length) {
          mj.pick = cmd[22];
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
        mj.state = 0;
        var pos = cmd[1];
        var p = mj.pCard[pos];
        switch (cmd[2] & 0xff)
        {
        case 1:                         // Pass.
          if (mj.onplayerpass) {
            mj.onplayerpass(pos);
          }
          break;
        case 2:                         // Exchange.
          mj.cDrop.push(cmd[3]);
          if (pos == mj.myPos && -1 != mj.pick && mj.pick != cmd[3]) {
            for (var i = 0; i < p.length; i++) {
              if (cmd[3] == p[i]) {
                p[i] = mj.pick;
                p.sort(sortNumber);     // Make sure sort by numbers.
                break;
              }
            }
          }
          renderGame();
          break;
        case 3:                         // Chi.
          var card = cmd[3];
          mj.oCard[pos].push(card, card + 1, card + 2);
          if (pos == mj.myPos) {
            p.push(mj.cDrop[mj.cDrop.length - 1]);
            p.sort(sortNumber);         // Make sure sort by numbers.
            [card, card + 1, card + 2].map(x => {var i = p.indexOf(x); if (-1 != i) p.splice(i, 1);} );
            mj.pick = p.pop();
          } else {
            p.splice(0, 3);
            mj.pick = -1;
          }
          mj.posPick = pos;
          mj.cDrop.pop();
          renderGame();
          break;
        case 4:                         // Pon.
          var card = cmd[3];
          mj.oCard[pos].push(card, card, card);
          if (pos == mj.myPos) {
            for (var i = 0; i < p.length; i++) {
              if (card == p[i]) {
                p.splice(i, 2);
                break;
              }
            }
            mj.pick = p.pop();
          } else {
            p.splice(0, 3);
            mj.pick = -1;
          }
          mj.posPick = pos;
          mj.cDrop.pop();
          renderGame();
          break;
        case 5:                         // Gun.
          var card = cmd[3];
          mj.oCard[pos].push(card, card, card, card);
          if (pos == mj.myPos) {
            for (var i = 0; i < p.length; i++) {
              if (card == p[i]) {
                p.splice(i, 3);
                break;
              }
            }
            mj.pick = cmd[4];           // Append from hdCards.
          } else {
            p.splice(0, 2);
            mj.pick = -1;
          }
          mj.posPick = pos;
          mj.cDrop.pop();
          renderGame();
          break;
        case 6:                         // Lon.
          mj.posPick = pos;
          mj.pick = cmd[3];
          renderGame();
          if (mj.ongameroundend) {
            var lon = [];
            for (var i = 4; i < cmd.length; i++) {
              lon.push(cmd[i]);
            }
            mj.ongameroundend(pos, lon);
          }
          break;
        case 7:                         // Pick.
          mj.posPick = pos;
          mj.pick = cmd[3];
          if (pos == mj.myPos) {
            parseState(cmd, 4);
          }
          renderGame();
          break;
        case 8:                         // State.
          parseState(cmd, 3);
          if (mj.ongamestate) {
            mj.ongamestate();
          }
          break;
        case 9:                         // Round end.
          if (mj.ongameroundend) {
            mj.ongameroundend(null, null);
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
              var idx = p.indexOf(c);
              if (-1 != idx) {
                p[idx] = c2;
              }
            }
          }
          if (pos == mj.myPos) {
            p.sort(sortNumber);         // Make sure sort by numbers.
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

  function parseState(cmd, idx) {
    var state = cmd[idx] & 0xff;
    mj.state = state;
    idx += 1;
    mj.tinCard = [];
    if (0 != (state & (1 << 2))) { // Can tin.
      var count = cmd[idx];
      idx = idx + 1;
      for (var i = idx; i < idx + count; i++) {
        mj.tinCard.push(cmd[i]);
      }
      idx += count;
    }
    mj.gunCard = [];
    if (0 != (state & (1 << 4))) { // Can gun.
      var count = cmd[idx];
      idx = idx + 1;
      for (var i = idx; i < idx + count; i++) {
        mj.gunCard.push(cmd[i]);
      }
      idx += count;
    }
    mj.ponCard = [];
    if (0 != (state & (1 << 5))) { // Can pon.
      var count = cmd[idx];
      idx = idx + 1;
      mj.ponCard.push(cmd[idx]);
      idx += count;
    }
    mj.chiCard = [];
    if (0 != (state & (1 << 6))) { // Can chi.
      var count = cmd[idx];
      idx = idx + 1;
      for (var i = idx; i < idx + count; i++) {
        mj.chiCard.push(cmd[i]);
      }
      idx += count;
    }
    mj.canLon = false;
    if (0 != (state & (1 << 3))) { // Can lon.
      mj.canLon = true;
    }
  }

  // Mahjong game protocols.

  this.changeName = function(name) {
    send(ws, '54,' + name);
  }

  this.newGame = function() {
    if (-1 == mj.myGameId) {
      send(ws, '32');
    }
  }

  this.leaveGame = function() {
    if (-1 != mj.myGameId) {
      send(ws, '34');
    }
  }

  this.joinGame = function(game) {
    if (-1 == mj.myGameId) {
      send(ws, '35,' + game);
    }
  }

  this.startGame = function() {
    if (-1 != mj.myGameId) {
      send(ws, '37');
    }
  }

  this.pass = function() {
    if (mj.state) {
      send(ws, '52,1');
    }
  }

  this.isMyTurn = function() {
    return -1 != mj.pick && -1 != mj.myGameId && mj.myPos == mj.posPick;
  }

  this.exchange = function(card) {
    if (this.isMyTurn()) {
      send(ws, '52,2,' + card);
    }
  }

  this.tin = function(card) {
    for (var i = 0; i < mj.tinCard.length; i++) {
      if (card == mj.tinCard[i]) {
        send(ws, '52,11,' + card);
        return;
      }
    }
  }

  this.gun = function(card) {
    for (var i = 0; i < mj.gunCard.length; i++) {
      if (card == mj.gunCard[i]) {
        send(ws, '52,5,' + card);
        return;
      }
    }
  }

  this.pon = function(card) {
    for (var i = 0; i < mj.ponCard.length; i++) {
      if (card == mj.ponCard[i]) {
        send(ws, '52,4,' + card);
        return;
      }
    }
  }

  this.chi = function(card) {
    for (var i = 0; i < mj.chiCard.length; i++) {
      if (card == mj.chiCard[i]) {
        send(ws, '52,3,' + card);
        return;
      }
    }
  }

  this.lon = function() {
    if (mj.canLon) {
      send(ws, '52,6');
    }
  }

  this.onkeypress = function(key) {
    if (this.KEY_PASS == key) {
      if (0 != mj.state) {
        mj.pass();
      } else if (mj.isMyTurn()) {
        mj.exchange(mj.pick);
      }
    } else {
      if (0 != mj.state) {
        var i = mj.isMyTurn() ? mj.pCard[mj.myPos].length : 0;
        for (; i < mj.tinCard.length; i++) {
          if (key == i) {
            mj.tin(mj.tinCard[i]);
            return;
          }
        }
        for (; i < mj.gunCard.length; i++) {
          if (key == i) {
            mj.gun(mj.gunCard[i]);
            return;
          }
        }
        for (; i < mj.ponCard.length; i++) {
          if (key == i) {
            mj.pon(mj.ponCard[i]);
            return;
          }
        }
        for (; i < mj.chiCard.length; i++) {
          if (key == i) {
            mj.chi(mj.chiCard[i]);
            return;
          }
        }
        if (i == key) {
          mj.lon();
        }
      } else if (mj.isMyTurn()) {
        if (-1 != key && key < mj.pCard[mj.myPos].length) {
          mj.exchange(mj.pCard[mj.myPos][key]);
        }
      }
    }
  }
}
