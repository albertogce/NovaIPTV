part of 'home_screen.dart';

class HomeCenterDashboard extends StatefulWidget {
  final int totalChannels;
  final int totalMovies;
  final int totalSeries;
  final int totalFavorites;
  final VoidCallback onSelectLive;
  final VoidCallback onSelectMovies;
  final VoidCallback onSelectSeries;
  final VoidCallback onSelectContinueWatching;
  final VoidCallback onSelectSettings;
  // Global search
  final String globalSearchQuery;
  final ValueChanged<String> onGlobalSearchChanged;
  final List<LiveChannel> allChannels;
  final List<VodMovie> allMovies;
  final List<Series> allSeries;
  final ValueChanged<String> onSearchSubmitted;
  final void Function(LiveChannel) onChannelTap;
  final void Function(VodMovie) onMovieTap;
  final void Function(Series) onSeriesTap;
  final XtreamApiClient client;

  const HomeCenterDashboard({
    super.key,
    required this.totalChannels,
    required this.totalMovies,
    required this.totalSeries,
    required this.totalFavorites,
    required this.onSelectLive,
    required this.onSelectMovies,
    required this.onSelectSeries,
    required this.onSelectContinueWatching,
    required this.onSelectSettings,
    required this.globalSearchQuery,
    required this.onGlobalSearchChanged,
    required this.allChannels,
    required this.allMovies,
    required this.allSeries,
    required this.onSearchSubmitted,
    required this.onChannelTap,
    required this.onMovieTap,
    required this.onSeriesTap,
    required this.client,
  });

  @override
  State<HomeCenterDashboard> createState() => _HomeCenterDashboardState();
}

class _HomeCenterDashboardState extends State<HomeCenterDashboard> {
  final FocusNode _searchFocusNode = FocusNode();
  final FocusNode _firstButtonFocusNode = FocusNode();

  @override
  void initState() {
    super.initState();
  }

  @override
  void dispose() {
    _searchFocusNode.dispose();
    _firstButtonFocusNode.dispose();
    super.dispose();
  }

  Future<void> _openSearchDialog() async {
    final query = await showDialog<String>(
      context: context,
      builder: (_) => _TvSearchDialog(initialQuery: widget.globalSearchQuery),
    );

    if (!mounted || query == null || query.isEmpty) return;
    widget.onGlobalSearchChanged(query);
    widget.onSearchSubmitted(query);
  }

  @override
  Widget build(BuildContext context) {
    final query = widget.globalSearchQuery.toLowerCase();
    final isSearching = query.isNotEmpty;

    final filteredChannels = isSearching
        ? widget.allChannels
              .where((c) => c.channelName.toLowerCase().contains(query))
              .toList()
        : <LiveChannel>[];
    final filteredMovies = isSearching
        ? widget.allMovies
              .where((m) => m.title.toLowerCase().contains(query))
              .toList()
        : <VodMovie>[];
    final filteredSeries = isSearching
        ? widget.allSeries
              .where((s) => s.title.toLowerCase().contains(query))
              .toList()
        : <Series>[];

    return Container(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFF0B1117), Color(0xFF12232A)],
        ),
      ),
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(24, 22, 24, 32),
        child: Center(
          child: ConstrainedBox(
            constraints: BoxConstraints(
              maxWidth: 1100,
              minHeight: MediaQuery.sizeOf(context).height - 54,
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.center,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Wrap(
                  alignment: WrapAlignment.center,
                  spacing: 10,
                  runSpacing: 10,
                  children: [
                    _LibraryStat(
                      icon: Icons.live_tv,
                      value: widget.totalChannels,
                      label: 'canales',
                      color: const Color(0xFF5DE0C2),
                    ),
                    _LibraryStat(
                      icon: Icons.movie_outlined,
                      value: widget.totalMovies,
                      label: 'películas',
                      color: const Color(0xFFFFC857),
                    ),
                    _LibraryStat(
                      icon: Icons.tv_outlined,
                      value: widget.totalSeries,
                      label: 'series',
                      color: const Color(0xFFFF6B4A),
                    ),
                  ],
                ),
                const SizedBox(height: 18),
                _SearchActionButton(
                  query: widget.globalSearchQuery,
                  focusNode: _searchFocusNode,
                  onPressed: _openSearchDialog,
                ),
                const SizedBox(height: 28),
                const Text(
                  'Explorar biblioteca',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 12),
                Wrap(
                  spacing: 16,
                  runSpacing: 16,
                  alignment: WrapAlignment.center,
                  children: [
                    _MenuCenterButton(
                      icon: Icons.live_tv,
                      label: 'Canales en Vivo',
                      accent: const Color(0xFF5DE0C2),
                      subtitle: null,
                      onPressed: widget.onSelectLive,
                      focusNode: _firstButtonFocusNode,
                      autofocus: true,
                    ),
                    _MenuCenterButton(
                      icon: Icons.movie,
                      label: 'Películas',
                      accent: const Color(0xFFFFC857),
                      subtitle: null,
                      onPressed: widget.onSelectMovies,
                    ),
                    _MenuCenterButton(
                      icon: Icons.tv_outlined,
                      label: 'Series',
                      accent: const Color(0xFFFF6B4A),
                      subtitle: null,
                      onPressed: widget.onSelectSeries,
                    ),
                    _MenuCenterButton(
                      icon: Icons.play_circle_fill,
                      label: 'Seguir viendo',
                      accent: const Color(0xFF5DE0C2),
                      subtitle: null,
                      onPressed: widget.onSelectContinueWatching,
                    ),
                  ],
                ),
                if (isSearching) ...[
                  Offstage(
                    offstage: true,
                    child: Column(
                      children: [
                        if (filteredChannels.isEmpty &&
                            filteredMovies.isEmpty &&
                            filteredSeries.isEmpty)
                          const Padding(
                            padding: EdgeInsets.only(top: 32),
                            child: Text(
                              'No se encontraron resultados',
                              style: TextStyle(
                                color: Colors.white54,
                                fontSize: 16,
                              ),
                            ),
                          )
                        else ...[
                          if (filteredChannels.isNotEmpty) ...[
                            _SearchResultSection(
                              title:
                                  'Canales en Vivo (${filteredChannels.length})',
                              icon: Icons.live_tv,
                              children: filteredChannels
                                  .take(10)
                                  .map(
                                    (ch) => _SearchResultTile(
                                      title: ch.channelName,
                                      subtitle: ch.currentEpgTitle.isNotEmpty
                                          ? ch.currentEpgTitle
                                          : null,
                                      leading: ch.channelLogo.isNotEmpty
                                          ? Image.network(
                                              ch.channelLogo,
                                              width: 40,
                                              height: 40,
                                              fit: BoxFit.contain,
                                              errorBuilder: (_, __, ___) =>
                                                  const Icon(
                                                    Icons.tv,
                                                    color: Colors.white54,
                                                    size: 32,
                                                  ),
                                            )
                                          : const Icon(
                                              Icons.tv,
                                              color: Colors.white54,
                                              size: 32,
                                            ),
                                      onTap: () => widget.onChannelTap(ch),
                                    ),
                                  )
                                  .toList(),
                            ),
                          ],
                          if (filteredMovies.isNotEmpty) ...[
                            _SearchResultSection(
                              title: 'Películas (${filteredMovies.length})',
                              icon: Icons.movie,
                              children: filteredMovies
                                  .take(10)
                                  .map(
                                    (m) => _SearchResultTile(
                                      title: m.title,
                                      subtitle: m.year > 0
                                          ? m.year.toString()
                                          : null,
                                      leading: m.logo.isNotEmpty
                                          ? Image.network(
                                              m.logo,
                                              width: 40,
                                              height: 56,
                                              fit: BoxFit.cover,
                                              errorBuilder: (_, __, ___) =>
                                                  const Icon(
                                                    Icons.movie,
                                                    color: Colors.amber,
                                                    size: 32,
                                                  ),
                                            )
                                          : const Icon(
                                              Icons.movie,
                                              color: Colors.amber,
                                              size: 32,
                                            ),
                                      onTap: () => widget.onMovieTap(m),
                                    ),
                                  )
                                  .toList(),
                            ),
                          ],
                          if (filteredSeries.isNotEmpty) ...[
                            _SearchResultSection(
                              title: 'Series (${filteredSeries.length})',
                              icon: Icons.tv_outlined,
                              children: filteredSeries
                                  .take(10)
                                  .map(
                                    (s) => _SearchResultTile(
                                      title: s.title,
                                      subtitle: null,
                                      leading: s.logo.isNotEmpty
                                          ? Image.network(
                                              s.logo,
                                              width: 40,
                                              height: 56,
                                              fit: BoxFit.cover,
                                              errorBuilder: (_, __, ___) =>
                                                  const Icon(
                                                    Icons.tv_outlined,
                                                    color: Colors.amber,
                                                    size: 32,
                                                  ),
                                            )
                                          : const Icon(
                                              Icons.tv_outlined,
                                              color: Colors.amber,
                                              size: 32,
                                            ),
                                      onTap: () => widget.onSeriesTap(s),
                                    ),
                                  )
                                  .toList(),
                            ),
                          ],
                        ],
                      ],
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _TvSearchDialog extends StatefulWidget {
  final String initialQuery;

  const _TvSearchDialog({required this.initialQuery});

  @override
  State<_TvSearchDialog> createState() => _TvSearchDialogState();
}

class _TvSearchDialogState extends State<_TvSearchDialog> {
  late final TextEditingController _controller;
  final FocusNode _inputFocusNode = FocusNode();
  final FocusNode _cancelFocusNode = FocusNode();
  final FocusNode _searchFocusNode = FocusNode();
  bool _cancelHasFocus = false;
  bool _searchHasFocus = false;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initialQuery);
  }

  @override
  void dispose() {
    _controller.dispose();
    _inputFocusNode.dispose();
    _cancelFocusNode.dispose();
    _searchFocusNode.dispose();
    super.dispose();
  }

  void _submit() => Navigator.of(context).pop(_controller.text.trim());

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Align(
        alignment: Alignment.topCenter,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(32, 20, 32, 0),
          child: Material(
            color: const Color(0xFF15212A),
            elevation: 16,
            shadowColor: Colors.black54,
            borderRadius: BorderRadius.circular(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 560),
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Buscar en tu biblioteca',
                      style: TextStyle(
                        fontSize: 22,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      'Escribe lo que buscas o pulsa abajo para acceder a las acciones.',
                      style: TextStyle(color: Colors.white60, fontSize: 13),
                    ),
                    const SizedBox(height: 20),
                    Focus(
                      onKeyEvent: (node, event) {
                        if (event is KeyDownEvent &&
                            event.logicalKey == LogicalKeyboardKey.arrowDown) {
                          _searchFocusNode.requestFocus();
                          return KeyEventResult.handled;
                        }
                        return KeyEventResult.ignored;
                      },
                      child: TextField(
                        controller: _controller,
                        focusNode: _inputFocusNode,
                        autofocus: true,
                        textInputAction: TextInputAction.search,
                        decoration: const InputDecoration(
                          hintText: 'Canal, película o serie',
                          prefixIcon: Icon(
                            Icons.search,
                            color: Color(0xFF5DE0C2),
                          ),
                        ),
                        onSubmitted: (_) => _submit(),
                      ),
                    ),
                    const SizedBox(height: 20),
                    Row(
                      children: [
                        Expanded(
                          child: Focus(
                            focusNode: _cancelFocusNode,
                            onFocusChange: (hasFocus) =>
                                setState(() => _cancelHasFocus = hasFocus),
                            onKeyEvent: (node, event) {
                              if (event is KeyDownEvent &&
                                  (event.logicalKey ==
                                          LogicalKeyboardKey.select ||
                                      event.logicalKey ==
                                          LogicalKeyboardKey.enter ||
                                      event.logicalKey ==
                                          LogicalKeyboardKey
                                              .gameButtonSelect)) {
                                Navigator.of(context).pop();
                                return KeyEventResult.handled;
                              }
                              if (event is KeyDownEvent &&
                                  event.logicalKey ==
                                      LogicalKeyboardKey.arrowUp) {
                                _inputFocusNode.requestFocus();
                                return KeyEventResult.handled;
                              }
                              return KeyEventResult.ignored;
                            },
                            child: OutlinedButton(
                              style: OutlinedButton.styleFrom(
                                foregroundColor: Colors.white,
                                backgroundColor: _cancelHasFocus
                                    ? const Color(
                                        0xFF5DE0C2,
                                      ).withValues(alpha: 0.16)
                                    : Colors.transparent,
                                side: BorderSide(
                                  color: _cancelHasFocus
                                      ? const Color(0xFF5DE0C2)
                                      : Colors.white54,
                                  width: _cancelHasFocus ? 2 : 1,
                                ),
                              ),
                              onPressed: () => Navigator.of(context).pop(),
                              child: const Text('Cancelar'),
                            ),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Focus(
                            focusNode: _searchFocusNode,
                            onFocusChange: (hasFocus) =>
                                setState(() => _searchHasFocus = hasFocus),
                            onKeyEvent: (node, event) {
                              if (event is KeyDownEvent &&
                                  (event.logicalKey ==
                                          LogicalKeyboardKey.select ||
                                      event.logicalKey ==
                                          LogicalKeyboardKey.enter ||
                                      event.logicalKey ==
                                          LogicalKeyboardKey
                                              .gameButtonSelect)) {
                                _submit();
                                return KeyEventResult.handled;
                              }
                              if (event is KeyDownEvent &&
                                  event.logicalKey ==
                                      LogicalKeyboardKey.arrowUp) {
                                _inputFocusNode.requestFocus();
                                return KeyEventResult.handled;
                              }
                              return KeyEventResult.ignored;
                            },
                            child: FilledButton.icon(
                              style: FilledButton.styleFrom(
                                backgroundColor: _searchHasFocus
                                    ? const Color(0xFFFF6B4A)
                                    : const Color(0xFFB94732),
                                side: BorderSide(
                                  color: _searchHasFocus
                                      ? Colors.white
                                      : Colors.transparent,
                                  width: 2,
                                ),
                              ),
                              onPressed: _submit,
                              icon: const Icon(Icons.search),
                              label: const Text('Buscar'),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _SearchActionButton extends StatelessWidget {
  final String query;
  final FocusNode focusNode;
  final VoidCallback onPressed;

  const _SearchActionButton({
    required this.query,
    required this.focusNode,
    required this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    return Focus(
      focusNode: focusNode,
      autofocus: false,
      onKeyEvent: (node, event) {
        if (event is KeyDownEvent &&
            (event.logicalKey == LogicalKeyboardKey.enter ||
                event.logicalKey == LogicalKeyboardKey.select ||
                event.logicalKey == LogicalKeyboardKey.gameButtonSelect)) {
          onPressed();
          return KeyEventResult.handled;
        }
        return KeyEventResult.ignored;
      },
      child: Builder(
        builder: (context) {
          final hasFocus = Focus.of(context).hasFocus;
          return GestureDetector(
            onTap: onPressed,
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 150),
              width: double.infinity,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
              decoration: BoxDecoration(
                color: hasFocus
                    ? const Color(0xFFFF6B4A)
                    : const Color(0xFF15212A),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(
                  color: hasFocus ? Colors.white : Colors.white12,
                  width: hasFocus ? 2 : 1,
                ),
              ),
              child: Row(
                children: [
                  Icon(
                    Icons.search,
                    color: hasFocus ? Colors.white : const Color(0xFF5DE0C2),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      query.isEmpty ? 'Buscar en todo el contenido' : query,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: query.isEmpty ? Colors.white70 : Colors.white,
                        fontSize: 15,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                  Icon(
                    Icons.arrow_forward_ios,
                    color: hasFocus ? Colors.white70 : Colors.white38,
                    size: 15,
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

class _SearchResultSection extends StatelessWidget {
  final String title;
  final IconData icon;
  final List<Widget> children;

  const _SearchResultSection({
    required this.title,
    required this.icon,
    required this.children,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(vertical: 12),
          child: Row(
            children: [
              Icon(icon, color: const Color(0xFFE91E63), size: 20),
              const SizedBox(width: 8),
              Text(
                title,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ],
          ),
        ),
        ...children,
        const SizedBox(height: 8),
      ],
    );
  }
}

class _SearchResultTile extends StatefulWidget {
  final String title;
  final String? subtitle;
  final Widget leading;
  final VoidCallback onTap;

  const _SearchResultTile({
    required this.title,
    required this.subtitle,
    required this.leading,
    required this.onTap,
  });

  @override
  State<_SearchResultTile> createState() => _SearchResultTileState();
}

class _SearchResultTileState extends State<_SearchResultTile> {
  bool _hasFocus = false;

  @override
  Widget build(BuildContext context) {
    return Focus(
      onFocusChange: (focused) => setState(() => _hasFocus = focused),
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
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        margin: const EdgeInsets.symmetric(vertical: 2),
        decoration: BoxDecoration(
          color: _hasFocus ? const Color(0xFF2A0A1A) : const Color(0xFF1E1E1E),
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
            color: _hasFocus ? const Color(0xFFE91E63) : Colors.white12,
            width: _hasFocus ? 2 : 1,
          ),
        ),
        child: ListTile(
          leading: ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: SizedBox(width: 40, height: 40, child: widget.leading),
          ),
          title: Text(
            widget.title,
            style: const TextStyle(color: Colors.white, fontSize: 14),
          ),
          subtitle: widget.subtitle != null
              ? Text(
                  widget.subtitle!,
                  style: const TextStyle(color: Colors.white54, fontSize: 12),
                )
              : null,
          trailing: const Icon(Icons.chevron_right, color: Colors.white38),
          onTap: widget.onTap,
        ),
      ),
    );
  }
}

class _MenuCenterButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color accent;
  final String? subtitle;
  final VoidCallback onPressed;
  final bool autofocus;
  final FocusNode? focusNode;

  const _MenuCenterButton({
    required this.icon,
    required this.label,
    required this.accent,
    this.subtitle,
    required this.onPressed,
    this.autofocus = false,
    this.focusNode,
  });

  @override
  Widget build(BuildContext context) {
    return Focus(
      focusNode: focusNode,
      autofocus: autofocus,
      onKeyEvent: (node, event) {
        if (event is KeyDownEvent &&
            (event.logicalKey == LogicalKeyboardKey.select ||
                event.logicalKey == LogicalKeyboardKey.enter ||
                event.logicalKey == LogicalKeyboardKey.gameButtonSelect)) {
          onPressed();
          return KeyEventResult.handled;
        }
        return KeyEventResult.ignored;
      },
      child: Builder(
        builder: (context) {
          final hasFocus = Focus.of(context).hasFocus;
          return GestureDetector(
            onTap: onPressed,
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 150),
              width: 176,
              height: 148,
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: hasFocus
                    ? accent.withValues(alpha: 0.9)
                    : const Color(0xFF15212A),
                borderRadius: BorderRadius.circular(18),
                border: Border.all(
                  color: hasFocus
                      ? Colors.white
                      : accent.withValues(alpha: 0.3),
                  width: hasFocus ? 2 : 1,
                ),
                boxShadow: hasFocus
                    ? [
                        BoxShadow(
                          color: accent.withValues(alpha: 0.35),
                          blurRadius: 22,
                          spreadRadius: 1,
                        ),
                      ]
                    : [],
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: hasFocus
                          ? Colors.white.withValues(alpha: 0.16)
                          : accent.withValues(alpha: 0.13),
                      shape: BoxShape.circle,
                    ),
                    child: Icon(
                      icon,
                      size: 30,
                      color: hasFocus ? Colors.white : accent,
                    ),
                  ),
                  const SizedBox(height: 12),
                  Text(
                    label,
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 15,
                      fontWeight: hasFocus
                          ? FontWeight.bold
                          : FontWeight.normal,
                    ),
                  ),
                  if (subtitle != null) ...[
                    const SizedBox(height: 4),
                    Text(
                      subtitle!,
                      style: TextStyle(
                        color: hasFocus ? Colors.white70 : Colors.white38,
                        fontSize: 11,
                      ),
                    ),
                  ],
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

class _PosterContent extends StatelessWidget {
  final String title;
  final String imageUrl;
  final IconData icon;
  final String metadata;
  final Color accent;

  const _PosterContent({
    required this.title,
    required this.imageUrl,
    required this.icon,
    required this.metadata,
    required this.accent,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: Stack(
            fit: StackFit.expand,
            children: [
              if (imageUrl.isNotEmpty)
                Image.network(
                  imageUrl,
                  fit: BoxFit.cover,
                  errorBuilder: (_, __, ___) => _PosterFallback(icon: icon),
                )
              else
                _PosterFallback(icon: icon),
              Positioned(
                left: 8,
                bottom: 8,
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: const Color(0xE60B1117),
                    borderRadius: BorderRadius.circular(6),
                    border: Border.all(color: accent.withValues(alpha: 0.7)),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 7,
                      vertical: 4,
                    ),
                    child: Text(
                      metadata,
                      style: TextStyle(
                        color: accent,
                        fontSize: 10,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(9, 8, 9, 9),
          child: Text(
            title,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 12,
              height: 1.15,
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
      ],
    );
  }
}

class _HistoryCard extends StatelessWidget {
  final _HistoryEntry entry;

  const _HistoryCard({required this.entry});

  @override
  Widget build(BuildContext context) {
    return Stack(
      fit: StackFit.expand,
      children: [
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: entry.logo.isNotEmpty
                  ? Image.network(
                      entry.logo,
                      fit: BoxFit.cover,
                      width: double.infinity,
                      errorBuilder: (_, __, ___) => Icon(
                        entry.type == 'movie' ? Icons.movie : Icons.tv_outlined,
                        color: Colors.amber,
                      ),
                    )
                  : Icon(
                      entry.type == 'movie' ? Icons.movie : Icons.tv_outlined,
                      color: Colors.amber,
                    ),
            ),
            Padding(
              padding: const EdgeInsets.all(7),
              child: Text(
                entry.title,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ],
        ),
        Positioned(
          top: 7,
          left: 7,
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: const Color(0xDD0B1117),
              borderRadius: BorderRadius.circular(6),
            ),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 4),
              child: Text(
                entry.type == 'movie' ? 'PELÍCULA' : 'SERIE',
                style: const TextStyle(
                  color: Colors.white70,
                  fontSize: 9,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 0.5,
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class _ContinueWatchingCard extends StatelessWidget {
  final WatchProgress progress;
  final String imageUrl;
  const _ContinueWatchingCard({required this.progress, required this.imageUrl});
  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: imageUrl.isEmpty
                ? const Center(
                    child: Icon(Icons.movie, size: 52, color: Colors.white54),
                  )
                : Image.network(
                    imageUrl,
                    width: double.infinity,
                    fit: BoxFit.cover,
                    errorBuilder: (_, __, ___) => const Center(
                      child: Icon(Icons.movie, size: 52, color: Colors.white54),
                    ),
                  ),
          ),
          Text(
            progress.title,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 6),
          Text(
            '${progress.position.inMinutes} min de ${progress.duration.inMinutes} min',
            style: const TextStyle(color: Colors.white70, fontSize: 11),
          ),
          const SizedBox(height: 5),
          LinearProgressIndicator(value: progress.fraction),
        ],
      ),
    ),
  );
}
