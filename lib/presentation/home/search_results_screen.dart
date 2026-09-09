import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:iptv_flutter/data/models/live_channel.dart';
import 'package:iptv_flutter/data/models/vod_movie.dart';
import 'package:iptv_flutter/data/models/series.dart';

class SearchResultsScreen extends StatefulWidget {
  final String query;
  final List<LiveChannel> channels;
  final List<VodMovie> movies;
  final List<Series> series;

  const SearchResultsScreen({
    super.key,
    required this.query,
    required this.channels,
    required this.movies,
    required this.series,
  });

  @override
  State<SearchResultsScreen> createState() => _SearchResultsScreenState();
}

class _MarqueeText extends StatefulWidget {
  final String text;
  final TextStyle style;
  const _MarqueeText({required this.text, required this.style});
  @override
  State<_MarqueeText> createState() => _MarqueeTextState();
}

class _MarqueeTextState extends State<_MarqueeText>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: const Duration(seconds: 7),
  )..repeat();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final painter = TextPainter(
        text: TextSpan(text: widget.text, style: widget.style),
        maxLines: 1,
        textDirection: Directionality.of(context),
      )..layout();
      final overflow = painter.width - constraints.maxWidth;
      if (overflow <= 0) return Text(widget.text, style: widget.style);
      const gap = 32.0;
      return SizedBox(
        height: painter.height,
        child: ClipRect(
          child: AnimatedBuilder(
            animation: _controller,
            builder: (_, child) => Transform.translate(
              offset: Offset(-(painter.width + gap) * _controller.value, 0),
              child: child,
            ),
            child: OverflowBox(
              alignment: Alignment.centerLeft,
              minWidth: 0,
              maxWidth: double.infinity,
              minHeight: painter.height,
              maxHeight: painter.height,
              child: SizedBox(
                width: painter.width * 2 + gap + 24,
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(widget.text, style: widget.style, maxLines: 1),
                    const SizedBox(width: gap),
                    Text(widget.text, style: widget.style, maxLines: 1),
                  ],
                ),
              ),
            ),
          ),
        ),
      );
    },
  );
}

class _SearchResultsScreenState extends State<SearchResultsScreen>
    with SingleTickerProviderStateMixin {
  late final List<_SearchTab> _tabs;
  late final TabController _tabController;
  int _selectedTabIndex = 0;

  @override
  void initState() {
    super.initState();
    final query = widget.query.toLowerCase();
    final channels = widget.channels
        .where((channel) => channel.channelName.toLowerCase().contains(query))
        .toList();
    final movies = widget.movies
        .where((movie) => movie.title.toLowerCase().contains(query))
        .toList();
    final series = widget.series
        .where((item) => item.title.toLowerCase().contains(query))
        .toList();

    _tabs = [
      if (channels.isNotEmpty) _SearchTab.channels(channels),
      if (movies.isNotEmpty) _SearchTab.movies(movies),
      if (series.isNotEmpty) _SearchTab.series(series),
    ];
    _tabController = TabController(
      length: _tabs.isEmpty ? 1 : _tabs.length,
      vsync: this,
    );
    _tabController.addListener(() {
      if (mounted && _selectedTabIndex != _tabController.index) {
        setState(() => _selectedTabIndex = _tabController.index);
      }
    });
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isEmpty = _tabs.isEmpty;
    return Scaffold(
      backgroundColor: const Color(0xFF0B1117),
      appBar: AppBar(
        backgroundColor: const Color(0xFF0B1117),
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Resultados de búsqueda',
              style: TextStyle(
                color: Colors.white,
                fontSize: 18,
                fontWeight: FontWeight.w700,
              ),
            ),
            Text(
              '"${widget.query}"',
              style: const TextStyle(color: Color(0xFF5DE0C2), fontSize: 13),
            ),
          ],
        ),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: Colors.white),
          onPressed: () => Navigator.of(context).pop(),
        ),
      ),
      body: isEmpty
          ? const Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.search_off, color: Colors.white38, size: 64),
                  SizedBox(height: 16),
                  Text(
                    'No se encontraron resultados',
                    style: TextStyle(color: Colors.white54),
                  ),
                ],
              ),
            )
          : Row(
              children: [
                Container(
                  width: 180,
                  margin: const EdgeInsets.fromLTRB(16, 8, 4, 16),
                  decoration: BoxDecoration(
                    color: const Color(0xFF15212A),
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(color: Colors.white12),
                  ),
                  child: NavigationRail(
                    backgroundColor: Colors.transparent,
                    selectedIndex: _selectedTabIndex,
                    extended: true,
                    minExtendedWidth: 180,
                    labelType: NavigationRailLabelType.none,
                    selectedIconTheme: const IconThemeData(
                      color: Color(0xFFFF6B4A),
                    ),
                    selectedLabelTextStyle: const TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.w700,
                    ),
                    unselectedIconTheme: const IconThemeData(
                      color: Color(0xFF8CAAA9),
                    ),
                    unselectedLabelTextStyle: const TextStyle(
                      color: Colors.white70,
                    ),
                    onDestinationSelected: (index) =>
                        _tabController.animateTo(index),
                    destinations: [
                      for (final tab in _tabs)
                        NavigationRailDestination(
                          icon: Icon(tab.icon),
                          selectedIcon: Icon(tab.icon),
                          label: Text(tab.label),
                        ),
                    ],
                  ),
                ),
                Expanded(
                  child: TabBarView(
                    controller: _tabController,
                    children: [for (final tab in _tabs) _SearchGrid(tab: tab)],
                  ),
                ),
              ],
            ),
    );
  }
}

enum _SearchTabType { channels, movies, series }

class _SearchTab {
  final _SearchTabType type;
  final List<Object> items;

  const _SearchTab({required this.type, required this.items});

  factory _SearchTab.channels(List<LiveChannel> items) =>
      _SearchTab(type: _SearchTabType.channels, items: items);
  factory _SearchTab.movies(List<VodMovie> items) =>
      _SearchTab(type: _SearchTabType.movies, items: items);
  factory _SearchTab.series(List<Series> items) =>
      _SearchTab(type: _SearchTabType.series, items: items);

  String get label => switch (type) {
    _SearchTabType.channels => 'Canales',
    _SearchTabType.movies => 'Películas',
    _SearchTabType.series => 'Series',
  };

  IconData get icon => switch (type) {
    _SearchTabType.channels => Icons.live_tv,
    _SearchTabType.movies => Icons.movie,
    _SearchTabType.series => Icons.tv_outlined,
  };
}

class _SearchGrid extends StatelessWidget {
  final _SearchTab tab;

  const _SearchGrid({required this.tab});

  @override
  Widget build(BuildContext context) {
    final crossAxisCount = MediaQuery.sizeOf(context).width >= 600 ? 7 : 2;

    return GridView.builder(
      padding: const EdgeInsets.all(12),
      gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: crossAxisCount,
        childAspectRatio: 0.8,
        crossAxisSpacing: 8,
        mainAxisSpacing: 8,
      ),
      itemCount: tab.items.length,
      itemBuilder: (context, index) {
        final item = tab.items[index];
        final title = switch (item) {
          LiveChannel channel => channel.channelName,
          VodMovie movie => movie.title,
          Series series => series.title,
          _ => '',
        };
        final image = switch (item) {
          LiveChannel channel => channel.channelLogo,
          VodMovie movie => movie.logo,
          Series series => series.logo,
          _ => '',
        };
        return _SearchGridCard(
          title: title,
          imageUrl: image,
          icon: tab.icon,
          autofocus: index == 0,
          onTap: () {
            Navigator.of(context).pop(item);
          },
        );
      },
    );
  }
}

class _SearchGridCard extends StatefulWidget {
  final String title;
  final String imageUrl;
  final IconData icon;
  final bool autofocus;
  final VoidCallback onTap;

  const _SearchGridCard({
    required this.title,
    required this.imageUrl,
    required this.icon,
    required this.autofocus,
    required this.onTap,
  });

  @override
  State<_SearchGridCard> createState() => _SearchGridCardState();
}

class _SearchGridCardState extends State<_SearchGridCard> {
  bool _hasFocus = false;

  @override
  Widget build(BuildContext context) {
    return Focus(
      autofocus: widget.autofocus,
      onFocusChange: (hasFocus) => setState(() => _hasFocus = hasFocus),
      onKeyEvent: (node, event) {
        if (event is KeyDownEvent &&
            (event.logicalKey == LogicalKeyboardKey.select ||
                event.logicalKey == LogicalKeyboardKey.enter ||
                event.logicalKey == LogicalKeyboardKey.gameButtonSelect)) {
          widget.onTap();
          return KeyEventResult.handled;
        }
        return KeyEventResult.ignored;
      },
      child: GestureDetector(
        onTap: widget.onTap,
        child: Card(
          color: _hasFocus ? const Color(0xFFFF6B4A) : const Color(0xFF15212A),
          margin: EdgeInsets.zero,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(8),
            side: BorderSide(
              color: _hasFocus ? const Color(0xFFFFC857) : Colors.white12,
              width: _hasFocus ? 2 : 1,
            ),
          ),
          child: Column(
            children: [
              Expanded(
                child: widget.imageUrl.isNotEmpty
                    ? Image.network(
                        widget.imageUrl,
                        fit: BoxFit.contain,
                        width: double.infinity,
                        errorBuilder: (_, __, ___) =>
                            Icon(widget.icon, color: Colors.amber, size: 36),
                      )
                    : Icon(widget.icon, color: Colors.amber, size: 36),
              ),
              Padding(
                padding: const EdgeInsets.all(4),
                child: _MarqueeText(
                  text: widget.title,
                  style: const TextStyle(color: Colors.white, fontSize: 12),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
