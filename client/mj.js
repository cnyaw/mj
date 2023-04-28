//
// mj.js
// Mahjong HTML5/WebSocket client.
//
// 2017/10/27 Waync Cheng.
//

var SW = 570, SH = 320;
var CW = 24, CH = 34;
var ctx2d;
var KEY_STRING = '1234567890-=jkl;,';
var KEY_CHAR = KEY_STRING.split('');
var mj = null;

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

function drawHotkey(x, y, ch) {
  ctx2d.font = '8pt bold';
  ctx2d.textAlign = 'center';
  ctx2d.fillStyle = 'red';
  ctx2d.fillText(ch, x + CW/2, y - 4);
  ctx2d.textAlign = '';
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
    if (mj.myPos == i) {
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
    if (0 != mj.oCard[i].length) {
      for (var j = 0; j < mj.oCard[i].length; j++, x = x + CW) {
        drawCard(mj.oCard[i][j], x, py[i]);
      }
      x = x + 2;
    }
    for (var j = 0; j < mj.pCard[i].length; j++, x = x + CW) {
      drawCard(mj.pCard[i][j], x, py[i]);
      if (mj.myPos == mj.posPick && mj.myPos == i) { // Show hotkey if this is my turn.
        drawHotkey(x, py[i], KEY_CHAR[j]);
      }
    }
    if (i == mj.myPos && mj.state) { // Draw my game state.
      var s = '';
      if (0 != (mj.state & (1 << 2))) { // Can tin.
        s += '可聽';
      }
      if (0 != (mj.state & (1 << 4))) { // Can gun.
        s += '可槓';
      }
      if (0 != (mj.state & (1 << 5))) { // Can pon.
        s += '可碰';
      }
      if (0 != (mj.state & (1 << 6))) { // Can chi.
        s += '可吃';
      }
      if (0 != (mj.state & (1 << 3))) { // Can lon.
        s += '可胡';
      }
      ctx2d.fillText(s, x + CW, py[i]);
    }
  }

  //
  // Render drop cards.
  //

  var ch = Math.floor(SW / CW);
  for (var i = 0; i < mj.cDrop.length; i++) {
    var x = 10 + CW * (i % ch);
    var y = 10 + 3.5 * CH + (CH + 2) * Math.floor(i / ch);
    drawCard(mj.cDrop[i], x, y);
  }

  //
  // Render pick.
  //

  if (-1 != mj.posPick) {
    var ox = 18, y = py[mj.posPick];
    if (0 != mj.oCard[mj.posPick].length) {
      ox += 2
    }
    drawCard(mj.pick, ox + CW * (mj.oCard[mj.posPick].length + mj.pCard[mj.posPick].length), y);
  }
}

function darkGameView(ctx2d) {
  ctx2d.globalAlpha = 0.65;
  ctx2d.fillStyle = 'black';
  ctx2d.fillRect(0, 0, SW, SH);
  ctx2d.globalAlpha = 1.0;
}

function WebSocketTest() {
  if (!("WebSocket" in window)) {
    writeToScreen("WebSocket NOT supported by your Browser!");
    return;
  }

  var scheme = document.location.protocol.replace('http', 'ws');
  if (-1 == scheme.indexOf('ws')) {
    scheme = 'ws:';
  }
  var addr = scheme + "//" + document.getElementById('svraddr').value + ":18802/";

  output = document.getElementById("output");
  imgCard = document.getElementById("imgCard");

  mj = new MahjongClient(addr, getName());

  mj.onconnect = function() {
  }

  mj.onlogin = function(id) {
    var addr = document.getElementById('svraddr');
    addr.parentNode.removeChild(addr);
    setLoginToolbar();
  }

  mj.ondisconnect = function() {
    mj.onerror("DISCONNECTED");
    mj = null;
  }

  mj.onerror = function(err) {
    writeToScreen('<span style="color:red;">ERROR:</span>' + err);
  }

  mj.onaddplayer = function(id, name) {
    addPlayer(id, name);
  }

  mj.onremoveplayer = function(id) {
    removePlayer(id);
  }

  mj.onsetplayername = function(id, name) {
    setPlayerName(id, name);
  }

  mj.onplayerpass = function(pos) {
    //writeToScreen('<span style="color:blue;">Player ' + pos + ' pass.</span>');
  }

  mj.onplayertin = function(pos, drop) {
    //writeToScreen('<span style="color:blue;">Player ' + pos + ' tin, drop ' + drop + '.</span>');
  }

  mj.onaddgame = function(game, p1, p2, p3, p4, isPlaying) {
    addGame(game, p1, p2, p3, p4, isPlaying);
    if (-1 != mj.myGameId) {
      removeButton('tbNewGame');
      removeButton('tbJoinGame');
    }
    if (p1 == mj.myId) {
      addStartGameButton();
    }
  }

  mj.onremovegame = function(game) {
    removeGame(game);
    if (game == mj.myGameId) {
      setLoginToolbar();
    }
  }

  mj.onjoingame = function(id, game, pos) {
    joinGame(id, game, pos);
    if (id == mj.myId) {
      removeButton('tbNewGame');
      removeButton('tbJoinGame');
    }
  }

  mj.onleavegame = function(id, game) {
    leaveGame(id, game);
    if (id == mj.myId) {
      setLoginToolbar();
    }
  }

  mj.ongamestart = function(game) {
    var g = document.getElementById('game' + game);
    g.classList.add('highlight');
    removeButton('tbStartGame');
  }

  mj.ongameroundstart = function(round, wind, windCount, total, master, masterCount) {
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
  }

  mj.ongameroundend = function(pos, lon) {
    darkGameView(ctx2d);
    if (pos) {
      // Player lon.
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
      for (var i = 0; i < lon.length; i++) {
        var n = lon[i];
        y = y + 30;
        ctx2d.fillText(WO_TYPE[n], x, y);
      }
    } else {
      // Game round is over.
      ctx2d.font = '46pt bold';
      ctx2d.textAlign = 'center';
      ctx2d.fillStyle = 'red';
      ctx2d.fillText('流局', SW / 2, SH / 2);
    }
  }

  mj.ongameend = function() {
    writeToScreen('<span style="color:blue;">Game is finish.</span>');
  }

  mj.ongameupdate = function() {
    renderGame();
  }
}

function writeToScreen(message) {
  var pre = document.createElement("p");
  pre.style.wordWrap = "break-word";
  pre.innerHTML = message;
  output.appendChild(pre);
  window.scrollTo(0, document.body.scrollHeight);
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
  if (id == mj.myId) {
    s = s + ' *';
    p.classList.add('highlight');
  }
  p.innerHTML = s;
}

function getName() {
  var name = encodeURIComponent(document.getElementById('nickname').value);
  return name;
}

function removePlayer(id) {
  var p = document.getElementById('player' + id);
  document.getElementById('player-list').removeChild(p);
}

function addGame(id, p1, p2, p3, p4, isPlaying) {
  var gl = document.getElementById('game-list');
  var g = document.createElement("p");
  var s = id + ' [' + p1 + ',' + p2 + ',' + p3 + ',' + p4 + ']';
  if (id == mj.myGameId) {
    s = s + ' *';
  }
  g.innerHTML = s;
  gl.appendChild(g);
  g.setAttribute("id", 'game' + id);
  if (isPlaying) {
    g.classList.add('highlight');
  }
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
  if (idGame == mj.myGameId) {
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
    if (mj.myId == m[i]) {
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

function getToolbarHtml() {
  var s = '<span>';
  s += '<a id="tbNewGame" class="btn" href="#" onclick="tbNewGame()">New Game</a>';
  s += ' <a id="tbJoinGame" class="btn" href="#" onclick="tbJoinGame()">Join Game</a>';
  s += ' <a class="btn" href="#" onclick="tbLeaveGame()">Leave Game</a>';
  s += '</span>';
  return s;
}

function setLoginToolbar() {
  var sse = document.getElementById('sse');
  sse.innerHTML = '<a class="btn" href="#" onclick="mj.changeName(getName());">Set Name </a><input type="text" id="nickname" value="nickname">';
  sse.innerHTML += getToolbarHtml();
}

function removeButton(id) {
  var b = document.getElementById(id);
  if (b) {
    b.parentElement.removeChild(b);
  }
}

function addStartGameButton() {
  var sse = document.getElementById('sse');
  sse.innerHTML += ' <a id="tbStartGame" class="btn" href="#" onclick="tbStartGame()">Start Game</a>';
}

function tbNewGame() {
  if (-1 != mj.myGameId) {
    mj.onerror('newgame: You are already in game.');
  } else {
    mj.newGame();
  }
}

function tbStartGame() {
  if (-1 == mj.myGameId) {
    mj.onerror('joingame: You are not in game.');
  } else {
    mj.startGame();
  }
}

function tbJoinGame() {
  if (-1 != mj.myGameId) {
    mj.onerror('joingame: You are already in game.');
  } else {
    var game = prompt('Enter game id:');
    if (game) {
      mj.joinGame(game);
    }
  }
}

function tbLeaveGame() {
  if (-1 == mj.myGameId) {
    mj.onerror('leavegame: You are not in game.');
  } else {
    mj.leaveGame();
  }
}

window.onkeypress = function(e) {
  e = e || window.event;
  if (mj.state) {
    if (' ' == e.key) {
      mj.pass();
    }
    return;
  }
  if (-1 == mj.myGameId || mj.myPos != mj.posPick) {
    return;
  }
  if (' ' == e.key) {
    mj.exchange(mj.pick);
    return;
  }
  var i = KEY_CHAR.indexOf(e.key);
  if (-1 != i && i < mj.pCard[mj.myPos].length) {
    mj.exchange(mj.pCard[mj.myPos][i]);
  }
}
