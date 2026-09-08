import 'package:flutter/material.dart';
import '../../data/api/xtream_api_client.dart';
import '../../data/models/live_channel.dart';
import '../../data/models/epg_program.dart';
import '../player/player_screen.dart';

class EpgGuideScreen extends StatefulWidget {
  final XtreamApiClient client; final List<LiveChannel> channels;
  const EpgGuideScreen({super.key, required this.client, required this.channels});
  @override State<EpgGuideScreen> createState() => _EpgGuideScreenState();
}
class _EpgGuideScreenState extends State<EpgGuideScreen> {
  String? _selected; final Map<String, List<EpgProgram>> _programs = {}; bool _loading = false;
  @override void initState() { super.initState(); if (widget.channels.isNotEmpty) _select(widget.channels.first); }
  LiveChannel? get _selectedChannel => widget.channels.where((c) => c.channelId.toString() == _selected).firstOrNull;
  String _streamUrl(LiveChannel channel) => '${widget.client.baseUrl.replaceAll(RegExp(r'/$'), '')}/${widget.client.username}/${widget.client.password}/${channel.channelId}';
  Future<void> _select(LiveChannel channel) async { setState(() { _selected = channel.channelId.toString(); _loading = true; }); try { final data = await widget.client.getShortEpg(streamId: channel.channelId); final list = (data['epg_listings'] as List? ?? []).whereType<Map>().map((e) => EpgProgram.fromJson(Map<String,dynamic>.from(e))).toList(); if (mounted) setState(() => _programs[channel.channelId.toString()] = list); } finally { if (mounted) setState(() => _loading = false); } }
  @override Widget build(BuildContext context) => Scaffold(appBar: AppBar(title: const Text('Guía EPG'), actions: [if (_selectedChannel != null) IconButton(icon: const Icon(Icons.play_circle_fill), tooltip: 'Reproducir canal', onPressed: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => PlayerScreen(streamUrl: _streamUrl(_selectedChannel!), channelName: _selectedChannel!.channelName))))]), body: Row(children: [SizedBox(width: 260, child: ListView(children: widget.channels.map((c) => ListTile(selected: _selected == c.channelId.toString(), leading: const Icon(Icons.live_tv), title: Text(c.channelName, maxLines: 1, overflow: TextOverflow.ellipsis), onTap: () => _select(c))).toList())), const VerticalDivider(width: 1), Expanded(child: _loading ? const Center(child: CircularProgressIndicator()) : _buildPrograms())]));
  Widget _buildPrograms() { final list = _programs[_selected] ?? []; if (list.isEmpty) return const Center(child: Text('No hay programación disponible')); return ListView.builder(padding: const EdgeInsets.all(20), itemCount: list.length, itemBuilder: (_, i) { final p = list[i]; return Padding(padding: const EdgeInsets.only(bottom: 14), child: Card(color: p.isLive ? const Color(0xFF1D403D) : null, child: Padding(padding: const EdgeInsets.all(18), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Row(children: [Text('${p.start.hour.toString().padLeft(2,'0')}:${p.start.minute.toString().padLeft(2,'0')}'), const SizedBox(width: 18), Expanded(child: Text(p.title, style: const TextStyle(fontWeight: FontWeight.bold))), if (p.isLive) const Text('EN EMISIÓN')]), const SizedBox(height: 10), if (p.isLive) LinearProgressIndicator(value: p.progress), if (p.description.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 10), child: Text(p.description, maxLines: 2, overflow: TextOverflow.ellipsis))])))); }); }
}
