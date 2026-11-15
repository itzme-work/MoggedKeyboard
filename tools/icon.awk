# ─────────────────────────────────────────────────────────────
#  Значок 512×512 для README и релиза.
#
#  На телефоне нет ни Python, ни ImageMagick, поэтому PNG пишется
#  здесь: палитра на 16 цветов, сглаживание краёв — пересечением
#  отрезков, сжатие «как есть» (stored), чтобы не тащить deflate.
#
#  Фигуры повторяют drawable/ic_launcher_foreground.xml:
#  клавиша 60×56 из 108 и буква M 32×28 внутри неё.
#
#  Запуск: sh tools/make-icon.sh → docs/icon-512.png
# ─────────────────────────────────────────────────────────────

function xor4(a, b,   r, k, i4, ba, bb) {
    # XOR двух чисел 0..15 — бит за битом.
    r = 0
    k = 1
    for (i4 = 0; i4 < 4; i4++) {
        ba = a % 2
        bb = b % 2
        if (ba != bb) r += k
        a = int(a / 2)
        b = int(b / 2)
        k *= 2
    }
    return r
}

function xor8(a, b) {
    return X4[int(a / 16), int(b / 16)] * 16 + X4[a % 16, b % 16]
}

function xor32(a, b,   r, p, i32) {
    r = 0
    p = 1
    for (i32 = 0; i32 < 4; i32++) {
        r += xor8(a % 256, b % 256) * p
        a = int(a / 256)
        b = int(b / 256)
        p *= 256
    }
    return r
}

# ── CRC32 и adler32 ──────────────────────────────────────────
function crcInit() { CRC = 4294967295 }
function crcByte(v) { CRC = xor32(CRCT[xor8(CRC % 256, v)], int(CRC / 256)) }
function crcValue() { return xor32(CRC, 4294967295) }
function adlerByte(v) {
    AD_A = (AD_A + v) % 65521
    AD_B = (AD_B + AD_A) % 65521
}

# ── вывод байтов ─────────────────────────────────────────────
function outByte(v) { printf "%c", v }

function outBe32(v) {
    outByte(int(v / 16777216) % 256)
    outByte(int(v / 65536) % 256)
    outByte(int(v / 256) % 256)
    outByte(v % 256)
}

# Начало блока PNG: длина, тип и первые байты CRC.
function beginChunk(b1, b2, b3, b4, len) {
    outBe32(len)
    crcInit()
    crcByte(b1); crcByte(b2); crcByte(b3); crcByte(b4)
    outByte(b1); outByte(b2); outByte(b3); outByte(b4)
}

function chunkByte(v) {
    crcByte(v)
    outByte(v)
}

function endChunk() { outBe32(crcValue()) }

# Заголовок несжатого блока deflate: 5 байт, они тоже в CRC.
function storedHeader(final, len,   i) {
    chunkByte(final)
    chunkByte(len % 256)
    chunkByte(int(len / 256) % 256)
    chunkByte((65535 - len) % 256)
    chunkByte(int((65535 - len) / 256) % 256)
}

# Байт сжатого потока: перед началом блока ставим его заголовок.
function zByte(v) {
    if (POS == 0) storedHeader(0, 65535)
    else if (POS == 65535) storedHeader(0, 65535)
    else if (POS == 131070) storedHeader(1, 514)
    crcByte(v)
    adlerByte(v)
    outByte(v)
    POS++
}

# Доля площади пикселей, накрытая отрезком [xa,xb].
function addSpan(arr, xa, xb, w,   i0, i1, x, lo, hi) {
    if (xb <= xa) return
    i0 = int(xa)
    i1 = int(xb)
    if (i0 < 0) i0 = 0
    if (i1 > WIDTH - 1) i1 = WIDTH - 1
    for (x = i0; x <= i1; x++) {
        lo = (x > xa) ? x : xa
        hi = (x + 1 < xb) ? x + 1 : xb
        if (hi > lo) arr[x] += w * (hi - lo)
    }
}

# Отрезок скруглённой клавиши на высоте yy.
function keySpan(yy,   dy, inset) {
    if (yy < KY0 || yy >= KY1) {
        SP0 = 1e9
        SP1 = -1e9
        return
    }
    inset = 0
    if (yy < KY0 + KR) {
        dy = KY0 + KR - yy
        inset = KR - sqrt(KR * KR - dy * dy)
    } else if (yy > KY1 - KR) {
        dy = yy - (KY1 - KR)
        inset = KR - sqrt(KR * KR - dy * dy)
    }
    SP0 = KX0 + inset
    SP1 = KX1 - inset
}

# Отрезки буквы M на высоте yy. Контур с вырезом сверху невыпуклый,
# поэтому считаем по правилу «чёт-нечет»: точки пересечения по порядку,
# заливка между первой и второй, третьей и четвёртой и так далее.
function mSpans(yy,   i, j, y1, y2, x, k) {
    MNUM = 0
    for (i = 1; i <= MN; i++) {
        j = (i % MN) + 1
        y1 = MY[i]
        y2 = MY[j]
        if (y1 == y2) continue
        if ((yy >= y1 && yy < y2) || (yy >= y2 && yy < y1)) {
            x = MX[i] + (yy - y1) * (MX[j] - MX[i]) / (y2 - y1)
            k = MNUM
            while (k > 0 && MS[k] > x) {
                MS[k + 1] = MS[k]
                k--
            }
            MS[k + 1] = x
            MNUM++
        }
    }
}

# Цвет пикселя: буква поверх клавиши, клавиша поверх фона.
function indexAt(x,   v) {
    if (MCOV[x] > 0.002) {
        v = 7 + int(MCOV[x] * 8 + 0.5)
        if (v > 15) v = 15
        return v
    }
    v = int(COV[x] * 7 + 0.5)
    if (v > 7) v = 7
    return v
}

BEGIN {
    WIDTH = 512
    HEIGHT = 512
    SUB = 4                      # подвыборок по вертикали на пиксель

    # ── палитра: 8 шагов фон → клавиша, 8 шагов клавиша → фон ──
    for (i = 0; i <= 7; i++) {
        t = i / 7
        PR[i] = int(103 + (246 - 103) * t + 0.5)
        PG[i] = int(80 + (240 - 80) * t + 0.5)
        PB[i] = int(164 + (250 - 164) * t + 0.5)
    }
    for (i = 8; i <= 15; i++) {
        t = (i - 7) / 8
        PR[i] = int(246 + (103 - 246) * t + 0.5)
        PG[i] = int(240 + (80 - 240) * t + 0.5)
        PB[i] = int(250 + (164 - 250) * t + 0.5)
    }

    # ── таблицы XOR и CRC32 ──
    for (a = 0; a < 16; a++)
        for (b = 0; b < 16; b++)
            X4[a, b] = xor4(a, b)
    for (n = 0; n < 256; n++) {
        c = n
        for (k = 0; k < 8; k++) {
            if (c % 2) c = xor32(3988292384, int(c / 2))
            else c = int(c / 2)
        }
        CRCT[n] = c
    }

    # ── геометрия (viewport 108 → 512) ──
    S = 512 / 108
    KX0 = 24 * S
    KX1 = 84 * S
    KY0 = 26 * S
    KY1 = 82 * S
    KR = 14 * S

    # Буква M — один контур 40×32 по центру клавиши (54,54): две стойки,
    # две диагонали и вырезы снизу. Тот же путь стоит в
    # ic_launcher_foreground.xml, поэтому в монохроме он вырезается дырой.
    MX[1] = 34 * S;  MY[1] = 70 * S
    MX[2] = 34 * S;  MY[2] = 38 * S
    MX[3] = 42 * S;  MY[3] = 38 * S
    MX[4] = 54 * S;  MY[4] = 54 * S
    MX[5] = 66 * S;  MY[5] = 38 * S
    MX[6] = 74 * S;  MY[6] = 38 * S
    MX[7] = 74 * S;  MY[7] = 70 * S
    MX[8] = 66 * S;  MY[8] = 70 * S
    MX[9] = 66 * S;  MY[9] = 50 * S
    MX[10] = 54 * S; MY[10] = 64 * S
    MX[11] = 42 * S; MY[11] = 50 * S
    MX[12] = 42 * S; MY[12] = 70 * S
    MN = 12

    # ── подпись PNG ──
    outByte(137); outByte(80); outByte(78); outByte(71)
    outByte(13); outByte(10); outByte(26); outByte(10)

    # ── IHDR: 512×512, палитра 4 бита ──
    beginChunk(73, 72, 68, 82, 13)
    chunkByte(0); chunkByte(0); chunkByte(2); chunkByte(0)
    chunkByte(0); chunkByte(0); chunkByte(2); chunkByte(0)
    chunkByte(4); chunkByte(3); chunkByte(0); chunkByte(0); chunkByte(0)
    endChunk()

    # ── PLTE ──
    beginChunk(80, 76, 84, 69, 48)
    for (i = 0; i < 16; i++) {
        chunkByte(PR[i]); chunkByte(PG[i]); chunkByte(PB[i])
    }
    endChunk()

    # ── IDAT: 2 байта заголовка + 3 блока по 5 байт + 512×257 + 4 байта adler ──
    beginChunk(73, 68, 65, 84, 131605)
    chunkByte(120); chunkByte(1)

    POS = 0
    AD_A = 1
    AD_B = 0

    for (y = 0; y < HEIGHT; y++) {
        for (x = 0; x < WIDTH; x++) {
            COV[x] = 0
            MCOV[x] = 0
        }
        for (s = 0; s < SUB; s++) {
            yy = y + (s + 0.5) / SUB
            keySpan(yy)
            addSpan(COV, SP0, SP1, 1 / SUB)
            mSpans(yy)
            for (q = 1; q + 1 <= MNUM; q += 2) {
                addSpan(MCOV, MS[q], MS[q + 1], 1 / SUB)
            }
        }

        zByte(0)                      # фильтр строки — без фильтра

        for (x = 0; x < WIDTH; x += 2) {
            zByte(indexAt(x) * 16 + indexAt(x + 1))
        }
    }

    # контрольная сумма zlib: B и A
    chunkByte(int(AD_B / 256) % 256)
    chunkByte(AD_B % 256)
    chunkByte(int(AD_A / 256) % 256)
    chunkByte(AD_A % 256)
    endChunk()

    # ── IEND ──
    beginChunk(73, 69, 78, 68, 0)
    endChunk()
}
