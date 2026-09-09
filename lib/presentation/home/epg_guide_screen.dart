// ignore_for_file: curly_braces_in_flow_control_structures

import 'package:flutter/material.dart';

import '../../data/api/xtream_api_client.dart';
import '../../data/models/epg_program.dart';
import '../../data/models/live_channel.dart';
import '../player/player_screen.dart';

class EpgGuideScreen extends StatefulWidget {
  final XtreamApiClient client;
  final List<LiveChannel> channels;

  const EpgGuideScreen({
    super.key,
    required this.client,
    required this.channels,
  });

  @override
  State<EpgGuideScreen> createState() => _EpgGuideScreenState();
}

class _EpgGuideScreenState extends State<EpgGuideScreen> {
  String? _selected;
  final Map<String, List<EpgProgram>> _programs = {};
  bool _loading = false;

  @override
  void initState() {
    super.initState();
    if (widget.channels.isNotEmpty) _select(widget.channels.first);
  }

  LiveChannel? get _selectedChannel => widget.channels
      .where((channel) => channel.channelId.toString() == _selected)
      .firstOrNull;

  String _streamUrl(LiveChannel channel) {
    final base = widget.client.baseUrl.replaceAll(RegExp(r'/$'), '');
    return '$base/live/${widget.client.username}/${widget.client.password}/${channel.channelId}.ts';
  }

  Future<void> _select(LiveChannel channel) async {
    final id = channel.channelId.toString();
    setState(() {
      _selected = id;
      _loading = !_programs.containsKey(id);
    });
    if (_programs.containsKey(id)) return;
    try {
      final data = await widget.client.getShortEpg(streamId: channel.channelId);
      final list = (data['epg_listings'] as List? ?? [])
          .whereType<Map>()
          .map((item) => EpgProgram.fromJson(Map<String, dynamic>.from(item)))
          .toList();
      if (mounted) setState(() => _programs[id] = list);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _playChannel() {
    final channel = _selectedChannel;
    if (channel == null) return;
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => PlayerScreen(
          streamUrl: _streamUrl(channel),
          channelName: channel.channelName,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Row(
          children: [
            SizedBox(
              width: 260,
              child: ListView(
                children: widget.channels
                    .map(
                      (channel) => ListTile(
                        selected: _selected == channel.channelId.toString(),
                        leading: const Icon(Icons.live_tv),
                        title: Text(
                          channel.channelName,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        onTap: () => _select(channel),
                      ),
                    )
                    .toList(),
              ),
            ),
            const VerticalDivider(width: 1),
            Expanded(
              child: _loading
                  ? const Center(child: CircularProgressIndicator())
                  : _buildPrograms(),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPrograms() {
    final list = _programs[_selected] ?? [];
    if (list.isEmpty)
      return const Center(child: Text('No hay programación disponible'));
    return ListView.builder(
      padding: const EdgeInsets.all(20),
      itemCount: list.length,
      itemBuilder: (_, index) {
        final program = list[index];
        return Padding(
          padding: const EdgeInsets.only(bottom: 14),
          child: Card(
            color: program.isLive ? const Color(0xFF1D403D) : null,
            child: InkWell(
              borderRadius: BorderRadius.circular(12),
              onTap: _playChannel,
              child: Padding(
                padding: const EdgeInsets.all(18),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Text(
                          '${program.start.hour.toString().padLeft(2, '0')}:${program.start.minute.toString().padLeft(2, '0')}',
                        ),
                        const SizedBox(width: 18),
                        Expanded(
                          child: Text(
                            program.title,
                            style: const TextStyle(fontWeight: FontWeight.bold),
                          ),
                        ),
                        if (program.isLive) const Text('EN EMISIÓN'),
                      ],
                    ),
                    const SizedBox(height: 10),
                    if (program.isLive)
                      LinearProgressIndicator(value: program.progress),
                    if (program.description.isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.only(top: 10),
                        child: Text(
                          program.description,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                  ],
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}
