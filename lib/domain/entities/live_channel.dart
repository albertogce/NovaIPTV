class LiveChannel {
  final int channelId;
  final String channelName;
  final String channelLogo;
  final String groupTitle;
  bool isFavorite;

  LiveChannel({
    required this.channelId,
    required this.channelName,
    required this.channelLogo,
    required this.groupTitle,
    this.isFavorite = false,
  });

  LiveChannel copyWith({
    int? channelId,
    String? channelName,
    String? channelLogo,
    String? groupTitle,
    bool? isFavorite,
  }) {
    return LiveChannel(
      channelId: channelId ?? this.channelId,
      channelName: channelName ?? this.channelName,
      channelLogo: channelLogo ?? this.channelLogo,
      groupTitle: groupTitle ?? this.groupTitle,
      isFavorite: isFavorite ?? this.isFavorite,
    );
  }
}