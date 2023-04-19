//
// mj.js
// Mahjong HTML5/WebSocket client.
//
// 2017/10/27 Waync Cheng.
//

var SW = 570, SH = 320;
var CW = 24, CH = 34;
var tHeartBeat;
var inGame = false;
var ctx2d;
var imgCard;
var myId, myGameId = -1, myPos;
var pCard, oCard;                       // Player cards, player open cards.
var cDrop;                              // Drop cards.
var pick, posPick;                      // Current pick card, current pick player pos.

function sortNumber(a, b) {
  return a - b;
}

function MJCOLOR(card) {
  return ((card) >> 8) & 0xf;
}

function MJNUM(card) {
  return card & 0xf;
}

function drawCard(card, x, y) {
  var col = MJCOLOR(card);
  var num = MJNUM(card) - 1;
  if (0 == card) {
    col = 3, num = 7;
  }
  ctx2d.drawImage(imgCard, num * CW, col * CH, CW, CH, x, y, CW, CH);
}

function renderGame() {

  //
  // Erase bkgnd.
  //

  ctx2d.fillStyle = 'white';
  ctx2d.fillRect(0, 0, SW, SH);

  //
  // Render players' cards.
  //

  var py = [];                          // Player y offset.
  var y = 10;
  for (var i = 0; i < 4; i++) {
    if (myPos == i) {
      py[i] = SH - 10 - CH;
    } else {
      py[i] = y;
      y = y + CH + 2;
    }
    var x = 2;
    ctx2d.fillStyle = 'black';
    ctx2d.font = '20px Arial';
    ctx2d.fillText(i, x, py[i] + 24);
    x = 16;
    if (0 != oCard[i].length) {
      for (var j = 0; j < oCard[i].length; j++, x = x + CW) {
        drawCard(oCard[i][j], x, py[i]);
      }
      x = x + 2;
    }
    for (var j = 0; j < pCard[i].length; j++, x = x + CW) {
      drawCard(pCard[i][j], x, py[i]);
    }
  }

  //
  // Render drop cards.
  //

  var ch = Math.floor(SW / CW);
  for (var i = 0; i < cDrop.length; i++) {
    var x = 10 + CW * (i % ch);
    var y = 10 + 3.5 * CH + (CH + 2) * Math.floor(i / ch);
    drawCard(cDrop[i], x, y);
  }

  //
  // Render pick.
  //

  if (-1 != posPick) {
    var ox = 18, y = py[posPick];
    if (0 != oCard[posPick].length) {
      ox += 2
    }
    drawCard(pick, ox + CW * (oCard[posPick].length + pCard[posPick].length), y);
  }
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

function darkGameView(ctx2d) {
  ctx2d.globalAlpha = 0.65;
  ctx2d.fillStyle = 'black';
  ctx2d.fillRect(0, 0, SW, SH);
  ctx2d.globalAlpha = 1.0;
}

function WebSocketTest()
{
  var output = document.getElementById("output");

  if (!("WebSocket" in window)) {
    writeToScreen("WebSocket NOT supported by your Browser!");
    return;
  }

  var scheme = document.location.protocol.replace('http', 'ws');
  if (-1 == scheme.indexOf('ws')) {
    scheme = 'ws:';
  }
  var wsUri = scheme + "//" + document.getElementById('svraddr').value + ":18802/";

  var ws = new WebSocket(wsUri, "mj");
  ws.binaryType = "arraybuffer";

  imgCard = document.getElementById("imgCard");

  ws.onopen = function (evt) {
    writeToScreen("CONNECTED");
    send(ws, encodeURIComponent(document.getElementById('nickname').value));
    tHeartBeat = setInterval(function(){ send(ws, '11'); }, 8000);
  };

  ws.onclose = function (evt) {
    clearInterval(tHeartBeat)
    writeToScreen("DISCONNECTED");
  };

  ws.onmessage = function (evt) {
    if (evt.data instanceof ArrayBuffer) {
      var b = new Uint8Array(evt.data);
      var s = '';
      for (var i = 1; i < b.length - 1; i++) {
        s = s + String.fromCharCode(b[i]);
      }

      posPick = -1;
      var cmd = s.split(',').map(Number); // Make sure convert to numbers.
      switch (cmd[0] & 0xff)
      {
      case 0xa:                         // Server is ready.
        myId = cmd[1];
        writeToScreen('<span style="color:blue;">Server is ready, my ID = ' + myId + '</span>');
        break;
      case 0x10:                        // New player.
        var scmd = s.split(',');
        //writeToScreen('<span style="color:blue;">New player: ' + scmd[1] + '(' + decodeURIComponent(scmd[2]) + ')</span>');
        addPlayer(scmd[1], decodeURIComponent(scmd[2]));
        break;
      case 0x11:                        // Player leave.
        //writeToScreen('<span style="color:red;">Player leave: ' + cmd[1] + '</span>');
        removePlayer(cmd[1]);
        break;
      case 0x20:                        // New game.
        //var s = '<span style="color:blue;">New game: ' + cmd[1] + ', [' + cmd[2] + ',' + cmd[3] + ',' + cmd[4] + ',' + cmd[5] + '], ';
        //if (0 != cmd[6]) {
        //  s = s + 'playing';
        //}
        //s = s + '</span>';
        //writeToScreen(s);
        addGame(cmd);

        //
        // For demo, try to join this game if not in game mode.
        //

        if (!inGame && 0 == cmd[6]) {
          send(ws, '35,' + cmd[1]);
          inGame = true
          writeToScreen('<span style="color:blue;">Join game: ' + cmd[1] + '</span>');
        }
        break;
      case 0x22:                        // Leave game.
        //writeToScreen('<span style="color:red;">Player ' + cmd[2] + ' leave game ' + cmd[1] + '</span>');
        if (-1 == cmd[2]) {
          removeGame(cmd[1]);
        } else {
          leaveGame(cmd[2], cmd[1]);
        }
        break;
      case 0x23:                        // Join game.
        //writeToScreen('<span style="color:blue;">Player ' + cmd[2] + ' join game ' + cmd[1] + ' at pos ' + cmd[3] + '</span>');
        if (myId == cmd[2]) {
          myGameId = cmd[1];
          myPos = cmd[3];
        }
        joinGame(cmd[2], cmd[1], cmd[3]);
        break;
      case 0x24:                        // Game is busy.
        writeToScreen('<span style="color:red;">Game is busy./span>');
        break;
      case 0x25:                        // Game start.
        //writeToScreen('<span style="color:red;">Game ' + cmd[1] + ' is playing.</span>');
        break;
      case 0x32:                        // Game init.
        var round = cmd[1];
        var wind = cmd[2];
        var windCount = cmd[3];
        var total = cmd[4];
        var master = cmd[5];
        var masterCount = cmd[6];

        cDrop = [];
        pCard = [];
        oCard = [];
        for (var i = 0; i < 4; i++) {
          oCard[i] = [];
          pCard[i] = [];
          for (var j = 0; j < 16; j++) {
            pCard[i][j] = 0;
          }
        }

        for (var i = 7; i < cmd.length; i++) {
          pCard[myPos][i - 7] = cmd[i];
        }

        var gv = document.createElement('div');
        output.appendChild(gv);

        var WindName = ["東","西","南","北"];
        var s = '第' + (1+round) + '圈' + WindName[wind] + '風' + WindName[windCount] + '局 遊戲:' + total + ', 莊家:' + master + ' 連莊:' + masterCount;
        var h = document.createElement('h1');
        h.appendChild(document.createTextNode(s));
        gv.appendChild(h);

        var c = document.createElement('canvas');
        c.setAttribute('id', 'canvas' + total);
        c.setAttribute('width', SW);
        c.setAttribute('height', SH);
        gv.appendChild(c);

        window.scrollTo(0, document.body.scrollHeight);

        ctx2d = c.getContext("2d");
        renderGame();
        break;
      case 0x33:                        // Game ends.
        //writeToScreen('<span style="color:blue;">Game is finish.</span>');
        break;
      case 0x34:                        // Game action.
        var pos = cmd[1];
        switch (cmd[2] & 0xff)
        {
        case 1:                         // Pass.
          //writeToScreen('<span style="color:blue;">Player ' + pos + ' pass.</span>');
          break;
        case 2:                         // Exchange.
          cDrop.push(cmd[3]);
          renderGame();
          break;
        case 3:                         // Chi.
          var card = cmd[3];
          oCard[pos].push(card, card + 1, card + 2);
          pCard[pos].splice(0, 3);
          if (pos != myPos) {
            posPick = pos;
            pick = 0;
          }
          cDrop.pop();
          renderGame();
          break;
        case 4:                         // Pon.
          var card = cmd[3];
          oCard[pos].push(card, card, card);
          pCard[pos].splice(0, 3);
          if (pos != myPos) {
            posPick = pos;
            pick = 0;
          }
          cDrop.pop();
          renderGame();
          break;
        case 5:                         // Gun.
          var card = cmd[3];
          oCard[pos].push(card, card, card, card);
          pCard[pos].splice(0, 3);
          if (pos != myPos) {
            posPick = pos;
            pick = 0;
          }
          cDrop.pop();
          renderGame();
          break;
        case 6:                         // Lon.
          posPick = pos;
          pick = cmd[3];
          renderGame();
          darkGameView(ctx2d);
          ctx2d.font = '32pt bold';
          ctx2d.textAlign = 'center';
          ctx2d.fillStyle = 'red';
          var x = SW / 2, y = SH / 2;
          ctx2d.fillText('玩家' + pos + '胡牌', x, y);
          ctx2d.font = '26pt bold';
          var WO_TYPE=
            ["門前清","平胡","斷么九","門前一杯口","面前聽","槓上開花","搶槓","海底撈月","河底撈魚","混帶么",
             "三色同順","一條龍","碰碰胡","三暗刻","三槓子","七對子","連風牌","八仙過海","混一色","純帶么",
             "三色同碰","小三元","混老頭","清一色","大三元","小四喜","大四喜","字一色","清老頭","四暗刻",
             "四槓子","全求人","五暗刻","天胡","地胡","人胡","風牌","台牌","立直","自摸",
             "胡牌","花牌","風牌","莊家","連莊","門清一摸三","紅中","青發","白板"];
          for (var i = 4; i < cmd.length; i++) {
            var n = cmd[i];
            y = y + 30;
            ctx2d.fillText(WO_TYPE[n], x, y);
          }
          break;
        case 7:                         // Pick.
          posPick = pos;
          pick = cmd[3];
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
        case 9:                         // End.
          darkGameView(ctx2d);
          ctx2d.font = '46pt bold';
          ctx2d.textAlign = 'center';
          ctx2d.fillStyle = 'red';
          ctx2d.fillText('流局', SW / 2, SH / 2);
          break;
        case 10:                        // Show cards.
          var idx = 3;
          for (var i = 0; i < 3; i++) {
            var pos = cmd[idx];
            var count = cmd[idx + 1];
            var offset = idx + 2 + count - pCard[pos].length;
            for (var j = 0; j < pCard[pos].length; j++) {
              var card = cmd[offset + j];
              pCard[pos][j] = card;
            }
            idx = idx + 2 + count;
          }
          renderGame();
          break;
        case 11:                        // Tin.
          //writeToScreen('<span style="color:blue;">Player ' + pos + ' tin, drop ' + cmd[3] + '.</span>');
          break;
        case 12:                        // Patch.
          for (var i = 3; i < cmd.length; i += 2) {
            var c = cmd[i];
            oCard[pos].push(c);
            var c2 = cmd[i + 1];
            if (0 != c2) {
              var idx = pCard[pos].indexOf(c);
              if (-1 != idx) {
                pCard[pos][idx] = c2;
              }
            }
          }
          if (pos == myPos) {
            pCard[pos].sort(sortNumber); // Make sure sort by numbers.
          }
          renderGame();
          break;
        }
        break;
      }
    } else {
      writeToScreen('<span style="color:blue;">RESPONSE:'+evt.data+'</span>');
    }
  };

  ws.onerror = function (evt) {
    clearInterval(tHeartBeat)
    writeToScreen('<span style="color:red;">ERROR:</span>' + evt.data);
  };

  function writeToScreen(message) {
    var pre = document.createElement("p");
    pre.style.wordWrap = "break-word";
    pre.innerHTML = message;
    output.appendChild(pre);
    window.scrollTo(0, document.body.scrollHeight);
  }
}

function addPlayer(id, name) {
  var pl = document.getElementById('player-list');
  var p = document.createElement("p");
  pl.appendChild(p);
  p.setAttribute("id", 'player' + id);
  setPlayerName(id, name);
}

function setPlayerName(id, name) {
  var p = document.getElementById('player' + id);
  var s = id + ' (' + name + ')';
  if (id == myId) {
    s = s + ' *';
  }
  p.innerHTML = s;
}

function removePlayer(id) {
  var p = document.getElementById('player' + id);
  document.getElementById('player-list').removeChild(p);
}

function addGame(cmd) {
  var gl = document.getElementById('game-list');
  var g = document.createElement("p");
  var s = cmd[1] + ' [' + cmd[2] + ',' + cmd[3] + ',' + cmd[4] + ',' + cmd[5] + ']';
  g.innerHTML = s;
  gl.appendChild(g);
  g.setAttribute("id", 'game' + cmd[1]);
}

function getPlayerListOfGame(s) {
  var s1 = s.indexOf('['), s2 = s.indexOf(']');
  return m = s.substr(s1 + 1, s2 - s1 - 1).split(',');
}

function joinGame(idPlayer, idGame, pos) {
  var g = document.getElementById('game' + idGame);
  var m = getPlayerListOfGame(g.innerHTML);
  m[pos] = idPlayer;
  var s = idGame + ' [' + m[0] + ',' + m[1] + ',' + m[2] + ',' + m[3] + ']';
  if (myGameId == idGame) {
    s = s + ' *';
  }
  g.innerHTML = s;
}

function leaveGame(idPlayer, idGame) {
  var g = document.getElementById('game' + idGame);
  var m = getPlayerListOfGame(g.innerHTML);
  for (var i = 0; i < m.length; i++) {
    if (m[i] == idPlayer) {
      m[i] = -1;
      break;
    }
  }
  var s = idGame + ' [' + m[0] + ',' + m[1] + ',' + m[2] + ',' + m[3] + ']';
  for (var i = 0; i < m.length; i++) {
    if (myId == m[i]) {
      s = s + ' *';
      break;
    }
  }
  g.innerHTML = s;
}

function removeGame(id) {
  var g = document.getElementById('game' + id);
  document.getElementById('game-list').removeChild(g);
}
