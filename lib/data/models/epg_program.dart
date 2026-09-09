import 'live_channel.dart';

class EpgProgram {
  final String title;
  final String description;
  final DateTime start;
  final DateTime end;
  const EpgProgram({
    required this.title,
    required this.description,
    required this.start,
    required this.end,
  });
  bool get isLive {
    final now = DateTime.now();
    return !now.isBefore(start) && now.isBefore(end);
  }

  double get progress => isLive
      ? ((DateTime.now().difference(start).inMilliseconds /
                    end.difference(start).inMilliseconds)
                .clamp(0, 1))
            .toDouble()
      : 0;
  factory EpgProgram.fromJson(Map<String, dynamic> json) {
    DateTime parse(dynamic value) {
      if (value is num) {
        final raw = value.toInt();
        return DateTime.fromMillisecondsSinceEpoch(
          raw < 100000000000 ? raw * 1000 : raw,
        );
      }
      final text = value?.toString().trim() ?? '';
      final numeric = int.tryParse(text);
      if (numeric != null) {
        return DateTime.fromMillisecondsSinceEpoch(
          numeric < 100000000000 ? numeric * 1000 : numeric,
        );
      }
      return DateTime.tryParse(text.replaceFirst(' ', 'T')) ?? DateTime.now();
    }

    String clean(dynamic value) => LiveChannel.cleanEpgTitle(value);
    return EpgProgram(
      title: clean(json['title'] ?? json['name'] ?? 'Sin título'),
      description: clean(json['description'] ?? json['desc'] ?? ''),
      start: parse(
        json['start'] ?? json['start_timestamp'] ?? json['start_time'],
      ),
      end: parse(json['end'] ?? json['stop_timestamp'] ?? json['stop_time']),
    );
  }
}
