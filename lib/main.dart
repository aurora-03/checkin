import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'models.dart';

const ink = Color(0xFF203F35);
const accent = Color(0xFF33755B);
const palette = [
  Color(0xFF33755B),
  Color(0xFFB57840),
  Color(0xFF627EAB),
  Color(0xFF9B6B86),
  Color(0xFF728447),
];
const symbols = [
  Icons.spa_outlined,
  Icons.auto_stories_outlined,
  Icons.directions_run_rounded,
  Icons.edit_note_rounded,
  Icons.wb_sunny_outlined,
];
void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const CheckInApp());
}

class CheckInApp extends StatelessWidget {
  const CheckInApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
    title: '日积 · 每日打卡',
    debugShowCheckedModeBanner: false,
    locale: const Locale('zh', 'CN'),
    supportedLocales: const [Locale('zh', 'CN')],
    localizationsDelegates: GlobalMaterialLocalizations.delegates,
    theme: ThemeData(
      useMaterial3: true,
      scaffoldBackgroundColor: const Color(0xFFF6F7F2),
      colorScheme: ColorScheme.fromSeed(seedColor: accent, primary: accent),
      appBarTheme: const AppBarTheme(
        backgroundColor: Color(0xFFF6F7F2),
        foregroundColor: ink,
        centerTitle: false,
      ),
      textTheme: const TextTheme(
        headlineLarge: TextStyle(
          fontSize: 32,
          fontWeight: FontWeight.w700,
          color: ink,
        ),
        titleLarge: TextStyle(
          fontSize: 21,
          fontWeight: FontWeight.w700,
          color: ink,
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: const Color(0xFFF2F4EF),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: BorderSide.none,
        ),
      ),
      cardTheme: CardThemeData(
        color: Colors.white,
        elevation: 0,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(22)),
      ),
      navigationBarTheme: const NavigationBarThemeData(
        backgroundColor: Colors.white,
        indicatorColor: Color(0xFFDCEBDD),
      ),
    ),
    home: const HomePage(),
  );
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});
  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> with WidgetsBindingObserver {
  static const storageKey = 'daily_check_v1';
  List<Habit> habits = [];
  bool loading = true;
  bool saving = false;
  String? loadError;
  int tab = 0;
  DateTime selected = DateUtils.dateOnly(DateTime.now());
  DateTime lastToday = DateUtils.dateOnly(DateTime.now());
  List<Habit> get active => habits.where((h) => !h.archived).toList();
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    load();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      final today = DateUtils.dateOnly(DateTime.now());
      if (selected == lastToday) setState(() => selected = today);
      lastToday = today;
    }
  }

  Future<void> load() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final raw = prefs.getString(storageKey);
      final loaded = raw == null ? <Habit>[] : decodeHabits(raw);
      if (mounted) {
        setState(() {
          habits = loaded;
          loading = false;
          loadError = null;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          loading = false;
          loadError = '读取本地数据失败。请重试，或从备份恢复。';
        });
      }
    }
  }

  Future<bool> commit(List<Habit> next) async {
    if (saving) return false;
    setState(() => saving = true);
    try {
      final prefs = await SharedPreferences.getInstance();
      if (!await prefs.setString(storageKey, encodeHabits(next))) {
        throw StateError('保存失败');
      }
      if (!mounted) return false;
      setState(() {
        habits = next;
        saving = false;
        loadError = null;
      });
      return true;
    } catch (_) {
      if (mounted) {
        setState(() => saving = false);
        toast('保存失败，请重试。本次更改尚未保存。');
      }
      return false;
    }
  }

  List<Habit> copy() => decodeHabits(encodeHabits(habits));
  void toast(String message) {
    if (mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(message)));
    }
  }

  Future<void> chooseDate() async {
    final value = await showDatePicker(
      context: context,
      initialDate: selected,
      firstDate: DateTime(2000),
      lastDate: DateTime.now(),
    );
    if (value != null && mounted) setState(() => selected = value);
  }

  Future<void> editHabit([Habit? habit]) async {
    final result = await showModalBottomSheet<Habit>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      backgroundColor: Colors.white,
      builder: (_) => HabitEditor(habit: habit),
    );
    if (result == null || !mounted) return;
    final next = copy();
    if (habit == null) {
      next.add(result);
    } else {
      final index = next.indexWhere((h) => h.id == habit.id);
      next[index] = Habit(
        id: result.id,
        title: result.title,
        unit: result.unit,
        dailyTarget: result.dailyTarget,
        goal: result.goal,
        colorIndex: result.colorIndex,
        archived: habit.archived,
        entries: next[index].entries,
      );
    }
    await commit(next);
  }

  Future<void> record(Habit habit) async {
    final date = dayKey(selected);
    final result = await showModalBottomSheet<DailyEntry>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      backgroundColor: Colors.white,
      builder: (_) =>
          EntryEditor(habit: habit, date: date, entry: habit.entries[date]),
    );
    if (result == null || !mounted) return;
    final next = copy();
    next.firstWhere((h) => h.id == habit.id).entries[date] = result;
    await commit(next);
  }

  Future<void> check(Habit habit) async {
    final next = copy();
    final h = next.firstWhere((h) => h.id == habit.id);
    final key = dayKey(selected);
    final existing = h.entries[key];
    h.entries[key] = DailyEntry(h.dailyTarget, existing?.note ?? '');
    if (await commit(next)) toast('已完成「${habit.title}」');
  }

  Future<bool> confirm(String title, String message) async =>
      await showDialog<bool>(
        context: context,
        builder: (c) => AlertDialog(
          title: Text(title),
          content: Text(message),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(c, false),
              child: const Text('取消'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(c, true),
              child: const Text('确认'),
            ),
          ],
        ),
      ) ??
      false;
  Future<void> action(String action, Habit habit) async {
    if (action == 'edit') {
      await editHabit(habit);
      return;
    }
    if (action == 'delete' &&
        !await confirm('删除任务？', '「${habit.title}」和全部历史记录将被删除。此操作无法撤销。')) {
      return;
    }
    if (action == 'clear' &&
        !await confirm('清除当天记录？', '${dayKey(selected)} 的完成数量和文字记录将被删除。')) {
      return;
    }
    if (!mounted) return;
    final next = copy();
    final h = next.firstWhere((h) => h.id == habit.id);
    if (action == 'delete') next.remove(h);
    if (action == 'archive') h.archived = !h.archived;
    if (action == 'clear') h.entries.remove(dayKey(selected));
    await commit(next);
  }

  Future<void> backup() async {
    await Clipboard.setData(ClipboardData(text: encodeHabits(habits)));
    toast('备份已复制，请粘贴到文件或笔记中妥善保存');
  }

  Future<void> restore() async {
    final text = await showDialog<String>(
      context: context,
      builder: (_) => const RestoreDialog(),
    );
    if (text == null || !mounted) return;
    try {
      final next = decodeHabits(text);
      if (!await confirm(
        '恢复备份？',
        '当前数据将被替换为备份中的 ${next.length} 个任务。建议先复制当前备份。',
      )) {
        return;
      }
      if (mounted && await commit(next)) toast('备份已恢复');
    } catch (_) {
      toast('备份格式无效，当前数据未更改');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Row(
          children: [
            Icon(Icons.spa_rounded, color: accent),
            SizedBox(width: 8),
            Text('日积', style: TextStyle(fontWeight: FontWeight.w800)),
            SizedBox(width: 10),
            Expanded(
              child: Text(
                '把每一天，过成积累',
                overflow: TextOverflow.ellipsis,
                style: TextStyle(fontSize: 12, color: Colors.black45),
              ),
            ),
          ],
        ),
        actions: [
          PopupMenuButton<String>(
            tooltip: '数据备份',
            onSelected: (v) {
              if (v == 'backup') {
                backup();
              } else {
                restore();
              }
            },
            itemBuilder: (_) => [
              PopupMenuItem(
                value: 'backup',
                enabled: !loading && loadError == null && !saving,
                child: const Text('复制数据备份'),
              ),
              PopupMenuItem(
                value: 'restore',
                enabled: !loading && !saving,
                child: const Text('粘贴备份恢复'),
              ),
            ],
          ),
        ],
      ),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : loadError != null
          ? Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(loadError!),
                    TextButton(onPressed: load, child: const Text('重试读取')),
                  ],
                ),
              ),
            )
          : AbsorbPointer(
              absorbing: saving,
              child: Stack(
                children: [
                  LayoutBuilder(
                    builder: (context, constraints) => Align(
                      alignment: Alignment.topCenter,
                      child: SizedBox(
                        width: constraints.maxWidth > 720
                            ? 720
                            : constraints.maxWidth,
                        child: ListView(
                          padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
                          children: tab == 0
                              ? todayView()
                              : tab == 1
                              ? goalsView()
                              : historyView(),
                        ),
                      ),
                    ),
                  ),
                  if (saving) const LinearProgressIndicator(),
                ],
              ),
            ),
      persistentFooterButtons: loadError == null && !loading
          ? [
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  onPressed: saving ? null : () => editHabit(),
                  icon: const Icon(Icons.add),
                  label: const Text('新建任务'),
                  style: FilledButton.styleFrom(
                    backgroundColor: ink,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 14),
                  ),
                ),
              ),
            ]
          : null,
      bottomNavigationBar: NavigationBar(
        selectedIndex: tab,
        onDestinationSelected: (v) => setState(() => tab = v),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.check_circle_outline),
            selectedIcon: Icon(Icons.check_circle),
            label: '每日',
          ),
          NavigationDestination(
            icon: Icon(Icons.flag_outlined),
            selectedIcon: Icon(Icons.flag),
            label: '目标',
          ),
          NavigationDestination(
            icon: Icon(Icons.calendar_month_outlined),
            selectedIcon: Icon(Icons.calendar_month),
            label: '记录',
          ),
        ],
      ),
    );
  }

  Widget heading(String eyebrow, String title, String subtitle) => Padding(
    padding: const EdgeInsets.only(bottom: 24),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          eyebrow,
          style: const TextStyle(
            color: accent,
            fontSize: 11,
            fontWeight: FontWeight.w700,
            letterSpacing: 2,
          ),
        ),
        const SizedBox(height: 8),
        Text(title, style: Theme.of(context).textTheme.headlineLarge),
        const SizedBox(height: 8),
        Text(
          subtitle,
          style: const TextStyle(color: Colors.black54, height: 1.6),
        ),
      ],
    ),
  );
  Widget dateBar() => Row(
    children: [
      IconButton(
        tooltip: '前一天',
        onPressed: selected.isAfter(DateTime(2000))
            ? () => setState(
                () => selected = DateTime(
                  selected.year,
                  selected.month,
                  selected.day - 1,
                ),
              )
            : null,
        icon: const Icon(Icons.chevron_left),
      ),
      Expanded(
        child: TextButton.icon(
          onPressed: chooseDate,
          icon: const Icon(Icons.calendar_today_outlined, size: 17),
          label: Text(
            '${selected.month}月${selected.day}日  ${['周一', '周二', '周三', '周四', '周五', '周六', '周日'][selected.weekday - 1]}${DateUtils.isSameDay(selected, DateTime.now()) ? ' · 今天' : ''}',
          ),
        ),
      ),
      IconButton(
        tooltip: '后一天',
        onPressed: DateUtils.isSameDay(selected, DateTime.now())
            ? null
            : () => setState(
                () => selected = DateTime(
                  selected.year,
                  selected.month,
                  selected.day + 1,
                ),
              ),
        icon: const Icon(Icons.chevron_right),
      ),
    ],
  );
  List<Widget> todayView() {
    final done = active.where((h) => h.done(selected)).length;
    return [
      heading('A LITTLE, EVERY DAY', '小小行动，慢慢生长。', '专注眼前的一步，让改变有迹可循。'),
      Container(
        padding: const EdgeInsets.all(24),
        decoration: BoxDecoration(
          color: ink,
          borderRadius: BorderRadius.circular(26),
        ),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    '当日完成',
                    style: TextStyle(color: Color(0xFFBFD5C6)),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    '$done / ${active.length}',
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 36,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    active.isEmpty
                        ? '从一个想坚持的小习惯开始'
                        : done == active.length
                        ? '今日目标已完成，给自己一点掌声。'
                        : '每一次行动，都在靠近目标。',
                    style: const TextStyle(
                      color: Color(0xFFCEDDCF),
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
            ),
            SizedBox(
              width: 70,
              height: 70,
              child: Stack(
                alignment: Alignment.center,
                children: [
                  SizedBox.expand(
                    child: CircularProgressIndicator(
                      value: active.isEmpty ? 0 : done / active.length,
                      strokeWidth: 6,
                      color: const Color(0xFFCADCA0),
                      backgroundColor: Colors.white12,
                    ),
                  ),
                  const Icon(
                    Icons.spa_outlined,
                    color: Color(0xFFCADCA0),
                    size: 32,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
      const SizedBox(height: 14),
      dateBar(),
      const SizedBox(height: 12),
      Row(
        children: [
          Text('每日任务', style: Theme.of(context).textTheme.titleLarge),
          const Spacer(),
          Text(
            '${active.length} 个进行中',
            style: const TextStyle(color: Colors.black45),
          ),
        ],
      ),
      const SizedBox(height: 14),
      if (active.isEmpty)
        empty('种下你的第一个小目标', '例如：每天阅读 20 页、运动 30 分钟，\n或简单地记录一天的心情。')
      else
        ...active.map(taskCard),
    ];
  }

  Widget menu(Habit h, {bool clear = true}) => PopupMenuButton<String>(
    tooltip: '任务选项',
    onSelected: (v) => action(v, h),
    itemBuilder: (_) => [
      const PopupMenuItem(value: 'edit', child: Text('编辑任务')),
      PopupMenuItem(
        value: 'archive',
        child: Text(h.archived ? '恢复任务' : '归档任务'),
      ),
      if (clear && h.entries.containsKey(dayKey(selected)))
        const PopupMenuItem(value: 'clear', child: Text('清除当天记录')),
      const PopupMenuItem(
        value: 'delete',
        child: Text('删除任务', style: TextStyle(color: Colors.red)),
      ),
    ],
  );
  Widget taskCard(Habit h) {
    final entry = h.entries[dayKey(selected)];
    final done = h.done(selected);
    final color = palette[h.colorIndex];
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: color.withValues(alpha: .10),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Icon(symbols[h.colorIndex], color: color),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        h.title,
                        style: const TextStyle(
                          fontWeight: FontWeight.w700,
                          fontSize: 17,
                        ),
                      ),
                      const SizedBox(height: 5),
                      Text(
                        '每日 ${number(h.dailyTarget)} ${h.unit}',
                        style: const TextStyle(
                          color: Colors.black45,
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ),
                ),
                menu(h),
              ],
            ),
            const SizedBox(height: 16),
            LinearProgressIndicator(
              value: ((entry?.amount ?? 0) / h.dailyTarget).clamp(0.0, 1.0),
              minHeight: 5,
              borderRadius: BorderRadius.circular(4),
              color: color,
              backgroundColor: color.withValues(alpha: .10),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: Text(
                    '${number(entry?.amount ?? 0)} / ${number(h.dailyTarget)} ${h.unit}',
                    style: TextStyle(color: color, fontWeight: FontWeight.w600),
                  ),
                ),
                TextButton(
                  onPressed: () => record(h),
                  child: Text(entry == null ? '写记录' : '编辑记录'),
                ),
                const SizedBox(width: 6),
                FilledButton.tonal(
                  onPressed: done ? () => record(h) : () => check(h),
                  style: FilledButton.styleFrom(
                    backgroundColor: done ? const Color(0xFFEAF0E6) : color,
                    foregroundColor: done ? accent : Colors.white,
                  ),
                  child: Text(done ? '✓ 已完成' : '打卡'),
                ),
              ],
            ),
            if (entry != null && entry.note.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(top: 6),
                child: Text(
                  entry.note,
                  style: const TextStyle(color: Colors.black54, height: 1.5),
                ),
              ),
          ],
        ),
      ),
    );
  }

  List<Widget> goalsView() => [
    heading('GROW AT YOUR OWN PACE', '把期待，变成进度。', '累计每一天的努力，走向自己设定的目标。'),
    if (habits.isEmpty) empty('还没有目标', '创建一个任务，同时设定它的累计目标。'),
    ...habits.map(
      (h) => Card(
        margin: const EdgeInsets.only(bottom: 14),
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(symbols[h.colorIndex], color: palette[h.colorIndex]),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      h.title,
                      style: const TextStyle(
                        fontWeight: FontWeight.w700,
                        fontSize: 18,
                      ),
                    ),
                  ),
                  if (h.archived)
                    const Text('已归档', style: TextStyle(color: Colors.black45)),
                  menu(h, clear: false),
                ],
              ),
              const SizedBox(height: 12),
              Text(
                '${number(h.total)} / ${number(h.goal)} ${h.unit}',
                style: const TextStyle(
                  fontSize: 26,
                  fontWeight: FontWeight.w700,
                  color: ink,
                ),
              ),
              const SizedBox(height: 14),
              LinearProgressIndicator(
                value: (h.total / h.goal).clamp(0.0, 1.0),
                minHeight: 8,
                borderRadius: BorderRadius.circular(8),
                color: palette[h.colorIndex],
                backgroundColor: const Color(0xFFF0F2EC),
              ),
              const SizedBox(height: 14),
              Text(
                h.total >= h.goal
                    ? '目标达成！你已经走了很远。'
                    : '已达成 ${(h.total / h.goal * 100).floor()}% · 当前连续达标 ${h.streak(DateTime.now())} 天',
                style: const TextStyle(color: Colors.black54, fontSize: 12),
              ),
              if (h.archived)
                const Padding(
                  padding: EdgeInsets.only(top: 8),
                  child: Text(
                    '归档后保留历史，可从菜单恢复。',
                    style: TextStyle(color: Colors.black45, fontSize: 12),
                  ),
                ),
            ],
          ),
        ),
      ),
    ),
  ];
  List<Widget> historyView() {
    final recorded = habits
        .where((h) => h.entries.containsKey(dayKey(selected)))
        .toList();
    final days = habits
        .expand(
          (h) => h.entries.entries
              .where((e) => e.value.amount > 0 || e.value.note.isNotEmpty)
              .map((e) => e.key),
        )
        .toSet();
    final monthDays = DateUtils.getDaysInMonth(selected.year, selected.month);
    final offset = DateTime(selected.year, selected.month, 1).weekday - 1;
    return [
      heading(
        'EVERY EFFORT COUNTS',
        '走过的路，都算数。',
        '共留下 ${days.length} 天记录。点击日期，回看自己的积累。',
      ),
      dateBar(),
      Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            children: [
              Row(
                children: [
                  for (final d in ['一', '二', '三', '四', '五', '六', '日'])
                    Expanded(
                      child: Center(
                        child: Text(d, style: TextStyle(color: Colors.black45)),
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 12),
              GridView.builder(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: 7,
                  mainAxisSpacing: 4,
                  crossAxisSpacing: 4,
                ),
                itemCount: offset + monthDays,
                itemBuilder: (_, index) {
                  if (index < offset) return const SizedBox.shrink();
                  final date = DateTime(
                    selected.year,
                    selected.month,
                    index - offset + 1,
                  );
                  final chosen = DateUtils.isSameDay(selected, date);
                  final future = date.isAfter(
                    DateUtils.dateOnly(DateTime.now()),
                  );
                  final hasData = days.contains(dayKey(date));
                  return InkWell(
                    borderRadius: BorderRadius.circular(12),
                    onTap: future
                        ? null
                        : () => setState(() => selected = date),
                    child: Container(
                      decoration: BoxDecoration(
                        color: chosen
                            ? ink
                            : hasData
                            ? const Color(0xFFE3EEDC)
                            : Colors.transparent,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Text(
                            '${date.day}',
                            style: TextStyle(
                              color: chosen
                                  ? Colors.white
                                  : future
                                  ? Colors.black26
                                  : ink,
                              fontWeight: chosen || hasData
                                  ? FontWeight.w700
                                  : FontWeight.normal,
                            ),
                          ),
                          if (hasData)
                            Container(
                              margin: const EdgeInsets.only(top: 3),
                              width: 4,
                              height: 4,
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                color: chosen ? Colors.white : accent,
                              ),
                            ),
                        ],
                      ),
                    ),
                  );
                },
              ),
            ],
          ),
        ),
      ),
      const SizedBox(height: 20),
      Text(
        '${selected.month}月${selected.day}日的记录',
        style: Theme.of(context).textTheme.titleLarge,
      ),
      const SizedBox(height: 12),
      if (recorded.isEmpty)
        empty('这一天，还没有记录', '到「每日」页打卡，或为这一天写点什么。')
      else
        ...recorded.map((h) {
          final entry = h.entries[dayKey(selected)]!;
          return Card(
            margin: const EdgeInsets.only(bottom: 12),
            child: ListTile(
              contentPadding: const EdgeInsets.fromLTRB(18, 10, 8, 10),
              leading: Icon(
                symbols[h.colorIndex],
                color: palette[h.colorIndex],
              ),
              title: Text(h.title),
              subtitle: Text(
                '${number(entry.amount)} ${h.unit}${h.done(selected) ? ' · 已达标' : ''}${entry.note.isEmpty ? '' : '\n${entry.note}'}',
                style: const TextStyle(height: 1.6),
              ),
              trailing: IconButton(
                tooltip: '编辑记录',
                icon: const Icon(Icons.edit_outlined, size: 20),
                onPressed: () => record(h),
              ),
            ),
          );
        }),
    ];
  }

  Widget empty(String title, String subtitle) => Container(
    padding: const EdgeInsets.symmetric(vertical: 36, horizontal: 20),
    alignment: Alignment.center,
    child: Column(
      children: [
        const Icon(Icons.eco_outlined, size: 42, color: Color(0xFF8BA58A)),
        const SizedBox(height: 14),
        Text(
          title,
          style: const TextStyle(
            fontSize: 17,
            fontWeight: FontWeight.w600,
            color: ink,
          ),
        ),
        const SizedBox(height: 10),
        Text(
          subtitle,
          textAlign: TextAlign.center,
          style: const TextStyle(color: Colors.black45, height: 1.7),
        ),
      ],
    ),
  );
}

class HabitEditor extends StatefulWidget {
  final Habit? habit;
  const HabitEditor({super.key, this.habit});
  @override
  State<HabitEditor> createState() => _HabitEditorState();
}

class _HabitEditorState extends State<HabitEditor> {
  final form = GlobalKey<FormState>();
  late final TextEditingController title;
  late final TextEditingController unit;
  late final TextEditingController daily;
  late final TextEditingController goal;
  late int color;
  @override
  void initState() {
    super.initState();
    final h = widget.habit;
    title = TextEditingController(text: h?.title ?? '');
    unit = TextEditingController(text: h?.unit ?? '次');
    daily = TextEditingController(text: number(h?.dailyTarget ?? 1));
    goal = TextEditingController(text: number(h?.goal ?? 30));
    color = h?.colorIndex ?? 0;
  }

  @override
  void dispose() {
    title.dispose();
    unit.dispose();
    daily.dispose();
    goal.dispose();
    super.dispose();
  }

  String? positive(String? value) {
    final n = double.tryParse(value ?? '');
    return n == null || !n.isFinite || n <= 0 || n > 1000000000
        ? '请输入大于 0、最多 10 亿的数值'
        : null;
  }

  @override
  Widget build(BuildContext context) => Padding(
    padding: EdgeInsets.fromLTRB(
      24,
      24,
      24,
      24 + MediaQuery.viewInsetsOf(context).bottom,
    ),
    child: SingleChildScrollView(
      child: Form(
        key: form,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              widget.habit == null ? '种下一个小目标' : '编辑任务',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 8),
            const Text(
              '给行动一个名字，也给坚持一个方向。',
              style: TextStyle(color: Colors.black45),
            ),
            if (widget.habit != null)
              const Padding(
                padding: EdgeInsets.only(top: 8),
                child: Text(
                  '修改单位不会换算历史数值；每日目标会用于判断历史达标状态。',
                  style: TextStyle(color: Colors.black54, fontSize: 12),
                ),
              ),
            const SizedBox(height: 24),
            TextFormField(
              controller: title,
              maxLength: 40,
              decoration: const InputDecoration(
                labelText: '任务名称',
                hintText: '例如：阅读、运动、记录心情',
              ),
              validator: (v) =>
                  v == null || v.trim().isEmpty ? '请输入任务名称' : null,
            ),
            const SizedBox(height: 12),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: TextFormField(
                    controller: daily,
                    decoration: const InputDecoration(labelText: '每日目标'),
                    keyboardType: const TextInputType.numberWithOptions(
                      decimal: true,
                    ),
                    validator: positive,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: TextFormField(
                    controller: unit,
                    maxLength: 8,
                    decoration: const InputDecoration(
                      labelText: '单位',
                      hintText: '次 / 页 / 分钟',
                    ),
                    validator: (v) =>
                        v == null || v.trim().isEmpty ? '请输入单位' : null,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: goal,
              decoration: const InputDecoration(
                labelText: '累计目标',
                helperText: '例如：每日 20 页，累计读完 600 页',
              ),
              keyboardType: const TextInputType.numberWithOptions(
                decimal: true,
              ),
              validator: positive,
            ),
            const SizedBox(height: 20),
            const Text('选择任务颜色'),
            const SizedBox(height: 10),
            Wrap(
              children: List.generate(
                palette.length,
                (i) => Padding(
                  padding: const EdgeInsets.only(right: 10),
                  child: IconButton.filledTonal(
                    tooltip: '样式 ${i + 1}',
                    onPressed: () => setState(() => color = i),
                    style: IconButton.styleFrom(
                      backgroundColor: palette[i].withValues(
                        alpha: color == i ? 1 : .10,
                      ),
                      foregroundColor: color == i ? Colors.white : palette[i],
                    ),
                    icon: Icon(symbols[i]),
                  ),
                ),
              ),
            ),
            const SizedBox(height: 24),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: () {
                  if (!form.currentState!.validate()) return;
                  Navigator.pop(
                    context,
                    Habit(
                      id:
                          widget.habit?.id ??
                          DateTime.now().microsecondsSinceEpoch.toString(),
                      title: title.text.trim(),
                      unit: unit.text.trim(),
                      dailyTarget: double.parse(daily.text),
                      goal: double.parse(goal.text),
                      colorIndex: color,
                    ),
                  );
                },
                child: const Padding(
                  padding: EdgeInsets.all(14),
                  child: Text('保存任务'),
                ),
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

class EntryEditor extends StatefulWidget {
  final Habit habit;
  final String date;
  final DailyEntry? entry;
  const EntryEditor({
    super.key,
    required this.habit,
    required this.date,
    this.entry,
  });
  @override
  State<EntryEditor> createState() => _EntryEditorState();
}

class _EntryEditorState extends State<EntryEditor> {
  final form = GlobalKey<FormState>();
  late final TextEditingController amount;
  late final TextEditingController note;
  @override
  void initState() {
    super.initState();
    amount = TextEditingController(text: number(widget.entry?.amount ?? 0));
    note = TextEditingController(text: widget.entry?.note ?? '');
  }

  @override
  void dispose() {
    amount.dispose();
    note.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Padding(
    padding: EdgeInsets.fromLTRB(
      24,
      24,
      24,
      24 + MediaQuery.viewInsetsOf(context).bottom,
    ),
    child: SingleChildScrollView(
      child: Form(
        key: form,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              widget.habit.title,
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 8),
            Text(
              '${widget.date} · 每日目标 ${number(widget.habit.dailyTarget)} ${widget.habit.unit}',
              style: const TextStyle(color: Colors.black45),
            ),
            const SizedBox(height: 24),
            TextFormField(
              controller: amount,
              keyboardType: const TextInputType.numberWithOptions(
                decimal: true,
              ),
              decoration: InputDecoration(
                labelText: '当天完成数量（${widget.habit.unit}）',
                helperText: '填写当天总量；只写日记时可填 0',
              ),
              validator: (v) {
                final n = double.tryParse(v ?? '');
                return n == null || !n.isFinite || n < 0 || n > 1000000000
                    ? '请输入 0 到 10 亿之间的数值'
                    : null;
              },
            ),
            const SizedBox(height: 18),
            TextFormField(
              controller: note,
              maxLines: 4,
              maxLength: 2000,
              decoration: const InputDecoration(
                labelText: '今天的记录',
                hintText: '做了什么？有什么收获？记下此刻的感受。',
              ),
              validator: (v) =>
                  (double.tryParse(amount.text) ?? 0) == 0 &&
                      (v == null || v.trim().isEmpty)
                  ? '请填写完成数量或文字记录'
                  : null,
            ),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: () {
                  if (form.currentState!.validate()) {
                    Navigator.pop(
                      context,
                      DailyEntry(double.parse(amount.text), note.text.trim()),
                    );
                  }
                },
                child: const Padding(
                  padding: EdgeInsets.all(14),
                  child: Text('保存记录'),
                ),
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

class RestoreDialog extends StatefulWidget {
  const RestoreDialog({super.key});
  @override
  State<RestoreDialog> createState() => _RestoreDialogState();
}

class _RestoreDialogState extends State<RestoreDialog> {
  final controller = TextEditingController();
  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('粘贴备份恢复'),
    content: SizedBox(
      width: 400,
      child: TextField(
        controller: controller,
        maxLines: 6,
        decoration: const InputDecoration(hintText: '在此粘贴完整的 JSON 备份'),
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('取消'),
      ),
      FilledButton(
        onPressed: () => Navigator.pop(context, controller.text.trim()),
        child: const Text('检查并恢复'),
      ),
    ],
  );
}
