import 'dart:convert';

String dayKey(DateTime date) =>
    '${date.year.toString().padLeft(4, '0')}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
String number(num value) => value == value.roundToDouble()
    ? value.toInt().toString()
    : value.toString();

class DailyEntry {
  final double amount;
  final String note;
  const DailyEntry(this.amount, this.note);
  Map<String, dynamic> toJson() => {'amount': amount, 'note': note};
  factory DailyEntry.fromJson(Map<String, dynamic> json) {
    final amount = (json['amount'] as num).toDouble();
    if (!amount.isFinite || amount < 0) throw const FormatException('记录数值无效');
    return DailyEntry(amount, json['note'] as String);
  }
}

class Habit {
  final String id;
  String title;
  String unit;
  double dailyTarget;
  double goal;
  int colorIndex;
  bool archived;
  final Map<String, DailyEntry> entries;
  Habit({
    required this.id,
    required this.title,
    this.unit = '次',
    this.dailyTarget = 1,
    this.goal = 30,
    this.colorIndex = 0,
    this.archived = false,
    Map<String, DailyEntry>? entries,
  }) : entries = entries ?? {};
  double get total =>
      entries.values.fold(0, (sum, entry) => sum + entry.amount);
  bool done(DateTime date) =>
      (entries[dayKey(date)]?.amount ?? 0) >= dailyTarget;
  int streak(DateTime today) {
    var date = DateTime(today.year, today.month, today.day);
    if (!done(date)) date = DateTime(date.year, date.month, date.day - 1);
    var result = 0;
    while (done(date)) {
      result++;
      date = DateTime(date.year, date.month, date.day - 1);
    }
    return result;
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'title': title,
    'unit': unit,
    'dailyTarget': dailyTarget,
    'goal': goal,
    'color': colorIndex,
    'archived': archived,
    'entries': entries.map((key, value) => MapEntry(key, value.toJson())),
  };
  factory Habit.fromJson(Map<String, dynamic> json) {
    final daily = (json['dailyTarget'] as num).toDouble();
    final goal = (json['goal'] as num).toDouble();
    if (!daily.isFinite ||
        daily <= 0 ||
        !goal.isFinite ||
        goal <= 0 ||
        (json['title'] as String).trim().isEmpty ||
        (json['unit'] as String).trim().isEmpty) {
      throw const FormatException('任务内容无效');
    }
    return Habit(
      id: json['id'] as String,
      title: json['title'] as String,
      unit: json['unit'] as String,
      dailyTarget: daily,
      goal: goal,
      colorIndex: (json['color'] as int).clamp(0, 4),
      archived: json['archived'] as bool,
      entries: (json['entries'] as Map<String, dynamic>).map((key, value) {
        if (DateTime.tryParse(key) == null ||
            dayKey(DateTime.parse(key)) != key) {
          throw const FormatException('日期无效');
        }
        return MapEntry(
          key,
          DailyEntry.fromJson(value as Map<String, dynamic>),
        );
      }),
    );
  }
}

String encodeHabits(List<Habit> habits) => jsonEncode({
  'version': 1,
  'habits': habits.map((habit) => habit.toJson()).toList(),
});
List<Habit> decodeHabits(String data) {
  final json = jsonDecode(data) as Map<String, dynamic>;
  if (json['version'] != 1) throw const FormatException('不支持的备份版本');
  final habits = (json['habits'] as List)
      .map((value) => Habit.fromJson(value as Map<String, dynamic>))
      .toList();
  if (habits.map((habit) => habit.id).toSet().length != habits.length) {
    throw const FormatException('重复的任务');
  }
  return habits;
}
