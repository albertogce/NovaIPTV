part of 'home_screen.dart';

class LiveChannelCard extends StatefulWidget {
  final LiveChannel channel;
  final XtreamApiClient client;

  const LiveChannelCard({
    super.key,
    required this.channel,
    required this.client,
  });

  @override
  State<LiveChannelCard> createState() => _LiveChannelCardState();
}

class _LiveChannelCardState extends State<LiveChannelCard> {
  String? _epgTitle;
  bool _loadingEpg = false;

  @override
  void initState() {
    super.initState();
    if (widget.channel.currentEpgTitle.isNotEmpty) {
      _epgTitle = widget.channel.currentEpgTitle;
    } else {
      _loadEpg();
    }
  }

  Future<void> _loadEpg() async {
    if (!mounted) return;
    setState(() => _loadingEpg = true);
    try {
      final data = await widget.client.getShortEpg(
        streamId: widget.channel.channelId,
      );
      final epgList = data['epg_listings'] as List<dynamic>?;
      if (epgList != null && epgList.isNotEmpty) {
        final current = epgList.first as Map<String, dynamic>;
        final rawTitle = current['title'] ?? current['now_playing'] ?? '';
        if (mounted) {
          setState(() {
            _epgTitle = LiveChannel.cleanEpgTitle(rawTitle);
            _loadingEpg = false;
          });
        }
      } else {
        if (mounted) setState(() => _loadingEpg = false);
      }
    } catch (_) {
      if (mounted) setState(() => _loadingEpg = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.start,
          children: [
            Expanded(
              child: Stack(
                fit: StackFit.expand,
                children: [
                  Container(
                    margin: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      gradient: const LinearGradient(
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                        colors: [Color(0xFF29434A), Color(0xFF17262E)],
                      ),
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(
                        color: Colors.white.withValues(alpha: 0.08),
                      ),
                    ),
                    padding: const EdgeInsets.all(12),
                    child: widget.channel.channelLogo.isNotEmpty
                        ? Image.network(
                            widget.channel.channelLogo,
                            fit: BoxFit.contain,
                            errorBuilder: (_, __, ___) => const Icon(
                              Icons.live_tv_rounded,
                              size: 38,
                              color: Colors.white70,
                            ),
                          )
                        : const Icon(
                            Icons.live_tv_rounded,
                            size: 38,
                            color: Colors.white70,
                          ),
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4.0),
              child: Text(
                widget.channel.channelName,
                textAlign: TextAlign.center,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
            const SizedBox(height: 2),
            Padding(
              padding: const EdgeInsets.only(
                left: 4.0,
                right: 4.0,
                bottom: 6.0,
              ),
              child: _loadingEpg
                  ? const SizedBox(
                      height: 10,
                      width: 10,
                      child: CircularProgressIndicator(
                        strokeWidth: 1.5,
                        color: Colors.amberAccent,
                      ),
                    )
                  : Text(
                      _epgTitle != null && _epgTitle!.isNotEmpty
                          ? _epgTitle!
                          : 'Sin guía EPG',
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                        color: Color(0xFF5DE0C2),
                        fontSize: 10,
                        fontWeight: FontWeight.w600,
                      ),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
            ),
          ],
        ),
        if (widget.channel.isFavorite)
          const Positioned(
            top: 12,
            right: 12,
            child: Icon(Icons.star, color: Color(0xFFFFC857), size: 18),
          ),
      ],
    );
  }
}

class CategoryContentView<C, T> extends StatefulWidget {
  final bool categoriesOnSide;
  final List<C> categories;
  final String? selectedCategoryId;
  final String Function(C) getCatId;
  final String Function(C) getCatName;
  final List<T> items;
  final Widget Function(T) itemBuilder;
  final ValueChanged<String?> onSelectCategory;
  final void Function(T)? onTap;
  final void Function(T)? onLongPress;
  final ReorderCallback? onReorder;
  final bool showReorderControls;
  final void Function(int, int)? onMoveItem;
  final FocusNode? Function(T item)? focusNodeForItem;
  // Search
  final String searchQuery;
  final ValueChanged<String> onSearchChanged;
  final VoidCallback onBack;
  final FocusNode? externalRightFocusNode;
  final List<Widget> headerActions;

  const CategoryContentView({
    super.key,
    required this.categories,
    required this.selectedCategoryId,
    required this.getCatId,
    required this.getCatName,
    required this.items,
    required this.itemBuilder,
    required this.onSelectCategory,
    required this.searchQuery,
    required this.onSearchChanged,
    required this.onBack,
    this.externalRightFocusNode,
    this.headerActions = const [],
    this.categoriesOnSide = false,
    this.onTap,
    this.onLongPress,
    this.onReorder,
    this.showReorderControls = false,
    this.onMoveItem,
    this.focusNodeForItem,
  });

  @override
  State<CategoryContentView<C, T>> createState() =>
      _CategoryContentViewState<C, T>();
}

class _CategoryContentViewState<C, T> extends State<CategoryContentView<C, T>> {
  final FocusNode _searchFocusNode = FocusNode();
  late TextEditingController _searchController;
  // FocusNode for the first grid item so we can focus it programmatically
  final FocusNode _gridFirstItemFocusNode = FocusNode();
  final Map<String, FocusNode> _sidebarCategoryFocusNodes = {};
  final ValueNotifier<String?> _focusedSidebarCategoryId = ValueNotifier(null);

  FocusNode _sidebarFocusNodeFor(String? categoryId) {
    final key = categoryId ?? '__all__';
    return _sidebarCategoryFocusNodes.putIfAbsent(key, FocusNode.new);
  }

  String? get _sidebarEntryCategoryId =>
      widget.selectedCategoryId ??
      (widget.categories.isEmpty
          ? null
          : widget.getCatId(widget.categories.first));

  void _focusSelectedSidebarCategory() {
    final targetId = _sidebarEntryCategoryId;
    _focusedSidebarCategoryId.value = targetId;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        FocusScope.of(context).requestFocus(_sidebarFocusNodeFor(targetId));
      }
    });
  }

  void _exitSidebarToGrid() {
    _focusedSidebarCategoryId.value = null;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _gridFirstItemFocusNode.requestFocus();
    });
  }

  @override
  void initState() {
    super.initState();
    _searchController = TextEditingController(text: widget.searchQuery)
      ..selection = TextSelection.collapsed(offset: widget.searchQuery.length);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && widget.items.isNotEmpty) {
        _gridFirstItemFocusNode.requestFocus();
      }
    });
  }

  @override
  void didUpdateWidget(covariant CategoryContentView<C, T> oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.searchQuery != widget.searchQuery) {
      _searchController.text = widget.searchQuery;
      _searchController.selection = TextSelection.collapsed(
        offset: widget.searchQuery.length,
      );
    }
  }

  @override
  void dispose() {
    _searchFocusNode.dispose();
    _searchController.dispose();
    _gridFirstItemFocusNode.dispose();
    _focusedSidebarCategoryId.dispose();
    for (final node in _sidebarCategoryFocusNodes.values) {
      node.dispose();
    }
    super.dispose();
  }

  /// Returns the category index currently selected (0 = "Todas", 1..n = category).
  int get _selectedCatIndex {
    if (widget.selectedCategoryId == null) return 0;
    final idx = widget.categories.indexWhere(
      (c) => widget.getCatId(c) == widget.selectedCategoryId,
    );
    return idx == -1 ? 0 : idx + 1; // +1 because index 0 is "Todas"
  }

  void _navigateCategoryLeft() {
    final current = _selectedCatIndex;
    if (current <= 0) return; // already at "Todas" or before
    final newIndex = current - 1;
    final newCatId = newIndex == 0
        ? null
        : widget.getCatId(widget.categories[newIndex - 1]);
    widget.onSelectCategory(newCatId);
    // After the rebuild the grid will have new items; focus first item
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && widget.items.isNotEmpty) {
        _gridFirstItemFocusNode.requestFocus();
      } else if (mounted) {
        _searchFocusNode.requestFocus();
      }
    });
  }

  void _navigateCategoryRight() {
    final current = _selectedCatIndex;
    final total = widget.categories.length + 1; // +1 for "Todas"
    if (current >= total - 1) return; // already at last category
    final newIndex = current + 1;
    final newCatId = widget.getCatId(widget.categories[newIndex - 1]);
    widget.onSelectCategory(newCatId);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && widget.items.isNotEmpty) {
        _gridFirstItemFocusNode.requestFocus();
      } else if (mounted) {
        _searchFocusNode.requestFocus();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFF0B1117), Color(0xFF10232B)],
        ),
      ),
      child: Column(
        children: [
          // Search bar and optional actions
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 4),
            child: Row(
              children: [
                Expanded(
                  child: Focus(
                    onFocusChange: (gained) {
                      // When outer Focus gains focus via D-Pad traversal, activate the TextField
                      if (gained && !_searchFocusNode.hasFocus) {
                        _searchFocusNode.requestFocus();
                      }
                    },
                    onKeyEvent: (node, event) {
                      if (event is KeyDownEvent) {
                        if (event.logicalKey == LogicalKeyboardKey.goBack ||
                            event.logicalKey == LogicalKeyboardKey.escape) {
                          widget.onBack();
                          return KeyEventResult.handled;
                        }
                        if (event.logicalKey == LogicalKeyboardKey.arrowDown) {
                          _gridFirstItemFocusNode.requestFocus();
                          return KeyEventResult.handled;
                        }
                        if (event.logicalKey == LogicalKeyboardKey.arrowRight &&
                            widget.externalRightFocusNode != null) {
                          widget.externalRightFocusNode!.requestFocus();
                          return KeyEventResult.handled;
                        }
                        if (event.logicalKey == LogicalKeyboardKey.escape) {
                          _gridFirstItemFocusNode.requestFocus();
                          return KeyEventResult.handled;
                        }
                      }
                      return KeyEventResult.ignored;
                    },
                    child: TextField(
                      focusNode: _searchFocusNode,
                      controller: _searchController,
                      onChanged: widget.onSearchChanged,
                      style: const TextStyle(color: Colors.white, fontSize: 14),
                      decoration: InputDecoration(
                        hintText: 'Buscar...',
                        hintStyle: const TextStyle(color: Colors.white38),
                        prefixIcon: const Icon(
                          Icons.search,
                          color: Color(0xFF5DE0C2),
                          size: 20,
                        ),
                        suffixIcon: widget.searchQuery.isNotEmpty
                            ? IconButton(
                                icon: const Icon(
                                  Icons.clear,
                                  color: Colors.white54,
                                  size: 18,
                                ),
                                onPressed: () {
                                  widget.onSearchChanged('');
                                  _gridFirstItemFocusNode.requestFocus();
                                },
                              )
                            : null,
                        isDense: true,
                        filled: true,
                        fillColor: const Color(0xFF15212A),
                        contentPadding: const EdgeInsets.symmetric(
                          vertical: 6,
                          horizontal: 12,
                        ),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(10),
                          borderSide: const BorderSide(color: Colors.white12),
                        ),
                        enabledBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(10),
                          borderSide: const BorderSide(color: Colors.white12),
                        ),
                        focusedBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(10),
                          borderSide: const BorderSide(
                            color: Color(0xFFFF6B4A),
                            width: 2,
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
                ...widget.headerActions,
              ],
            ),
          ),
          if (!widget.categoriesOnSide)
            // Horizontal category selector
            Container(
              height: 54,
              margin: const EdgeInsets.fromLTRB(16, 4, 16, 8),
              decoration: BoxDecoration(
                color: const Color(0xFF15212A).withValues(alpha: 0.9),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(
                  color: const Color(0xFF5DE0C2).withValues(alpha: 0.18),
                ),
              ),
              child: ListView.builder(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(
                  horizontal: 10,
                  vertical: 7,
                ),
                itemCount: widget.categories.length + 1,
                itemBuilder: (context, index) {
                  final isAll = index == 0;
                  final catId = isAll
                      ? null
                      : widget.getCatId(widget.categories[index - 1]);
                  final isSelected = isAll
                      ? widget.selectedCategoryId == null
                      : widget.selectedCategoryId.toString() ==
                            catId.toString();
                  final title = isAll
                      ? 'Todas'
                      : widget.getCatName(widget.categories[index - 1]);

                  return Padding(
                    padding: const EdgeInsets.only(right: 8.0),
                    child: Focus(
                      onKeyEvent: (node, event) {
                        if (event is KeyDownEvent &&
                            (event.logicalKey == LogicalKeyboardKey.select ||
                                event.logicalKey == LogicalKeyboardKey.enter ||
                                event.logicalKey ==
                                    LogicalKeyboardKey.gameButtonSelect)) {
                          widget.onSelectCategory(catId);
                          return KeyEventResult.handled;
                        }
                        return KeyEventResult.ignored;
                      },
                      child: Builder(
                        builder: (context) {
                          final hasFocus = Focus.of(context).hasFocus;
                          return ChoiceChip(
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(8),
                              side: BorderSide(
                                color: isSelected
                                    ? Colors.white24
                                    : Colors.transparent,
                              ),
                            ),
                            padding: const EdgeInsets.symmetric(horizontal: 8),
                            avatar: Icon(
                              isAll ? Icons.apps : Icons.label_outline,
                              size: 16,
                              color: isSelected || hasFocus
                                  ? Colors.white
                                  : const Color(0xFF5DE0C2),
                            ),
                            label: Text(
                              title,
                              style: TextStyle(
                                color: isSelected || hasFocus
                                    ? Colors.white
                                    : Colors.white60,
                                fontWeight: isSelected || hasFocus
                                    ? FontWeight.bold
                                    : FontWeight.normal,
                              ),
                            ),
                            selected: isSelected,
                            selectedColor: const Color(0xFFFF6B4A),
                            backgroundColor: hasFocus
                                ? const Color(0xFFB94732)
                                : const Color(0xFF1D3039),
                            onSelected: (_) => widget.onSelectCategory(catId),
                          );
                        },
                      ),
                    ),
                  );
                },
              ),
            ),
          if (!widget.categoriesOnSide)
            Container(
              height: 1,
              margin: const EdgeInsets.symmetric(horizontal: 16),
              color: const Color(0xFF5DE0C2).withValues(alpha: 0.12),
            ),
          // Grid content view with 7 columns
          if (widget.categoriesOnSide)
            Expanded(
              child: Row(
                children: [
                  _CategorySidebar<C>(
                    categories: widget.categories,
                    selectedCategoryId: widget.selectedCategoryId,
                    getCatId: widget.getCatId,
                    getCatName: widget.getCatName,
                    onSelectCategory: widget.onSelectCategory,
                    focusNodeForCategory: _sidebarFocusNodeFor,
                    focusedCategoryId: _focusedSidebarCategoryId,
                    onExitRight: _exitSidebarToGrid,
                  ),
                  Expanded(
                    child: GridViewContentView<T>(
                      key: ValueKey(widget.selectedCategoryId ?? '__all__'),
                      items: widget.items,
                      itemBuilder: widget.itemBuilder,
                      onTap: widget.onTap,
                      onLongPress: widget.onLongPress,
                      onReorder: widget.onReorder,
                      showReorderControls: widget.showReorderControls,
                      onMoveItem: widget.onMoveItem,
                      focusNodeForItem: widget.focusNodeForItem,
                      firstItemFocusNode: _gridFirstItemFocusNode,
                      onAtLeftEdge: _focusSelectedSidebarCategory,
                      onAtRightEdge: _navigateCategoryRight,
                    ),
                  ),
                ],
              ),
            )
          else
            Expanded(
              child: GridViewContentView<T>(
                key: ValueKey(widget.selectedCategoryId ?? '__all__'),
                items: widget.items,
                itemBuilder: widget.itemBuilder,
                onTap: widget.onTap,
                onLongPress: widget.onLongPress,
                onReorder: widget.onReorder,
                showReorderControls: widget.showReorderControls,
                onMoveItem: widget.onMoveItem,
                focusNodeForItem: widget.focusNodeForItem,
                firstItemFocusNode: _gridFirstItemFocusNode,
                onAtLeftEdge: _navigateCategoryLeft,
                onAtRightEdge: _navigateCategoryRight,
              ),
            ),
        ],
      ),
    );
  }
}

class _CategorySidebar<C> extends StatelessWidget {
  final List<C> categories;
  final String? selectedCategoryId;
  final String Function(C) getCatId;
  final String Function(C) getCatName;
  final ValueChanged<String?> onSelectCategory;
  final FocusNode Function(String? categoryId) focusNodeForCategory;
  final ValueNotifier<String?> focusedCategoryId;
  final VoidCallback onExitRight;

  const _CategorySidebar({
    required this.categories,
    required this.selectedCategoryId,
    required this.getCatId,
    required this.getCatName,
    required this.onSelectCategory,
    required this.focusNodeForCategory,
    required this.focusedCategoryId,
    required this.onExitRight,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: MediaQuery.sizeOf(context).width < 700 ? 148 : 210,
      margin: const EdgeInsets.fromLTRB(16, 6, 4, 20),
      decoration: BoxDecoration(
        color: const Color(0xFF15212A).withValues(alpha: 0.92),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
      ),
      child: ListView(
        padding: const EdgeInsets.all(8),
        children: List.generate(categories.length, (index) {
          final category = categories[index];
          final id = getCatId(category);
          final selected = id.toString() == selectedCategoryId.toString();
          final label = getCatName(category);

          final focusKey = id;
          return ValueListenableBuilder<String?>(
            valueListenable: focusedCategoryId,
            builder: (context, focusedCategory, _) => Focus(
              focusNode: focusNodeForCategory(id),
              onFocusChange: (hasFocus) {
                if (hasFocus) focusedCategoryId.value = focusKey;
              },
              onKeyEvent: (node, event) {
                if (event is! KeyDownEvent) return KeyEventResult.ignored;
                if (event.logicalKey == LogicalKeyboardKey.arrowRight) {
                  onExitRight();
                  return KeyEventResult.handled;
                }
                if (event.logicalKey == LogicalKeyboardKey.select ||
                    event.logicalKey == LogicalKeyboardKey.enter ||
                    event.logicalKey == LogicalKeyboardKey.gameButtonSelect) {
                  onSelectCategory(id);
                  WidgetsBinding.instance.addPostFrameCallback(
                    (_) => onExitRight(),
                  );
                  return KeyEventResult.handled;
                }
                return KeyEventResult.ignored;
              },
              child: Builder(
                builder: (context) {
                  final hasFocus = focusedCategory == focusKey;
                  return Padding(
                    padding: const EdgeInsets.only(bottom: 4),
                    child: Material(
                      color: Colors.transparent,
                      child: InkWell(
                        borderRadius: BorderRadius.circular(10),
                        onTap: () => onSelectCategory(id),
                        child: AnimatedContainer(
                          duration: const Duration(milliseconds: 160),
                          padding: const EdgeInsets.symmetric(
                            horizontal: 10,
                            vertical: 11,
                          ),
                          decoration: BoxDecoration(
                            color: hasFocus
                                ? const Color(
                                    0xFF5DE0C2,
                                  ).withValues(alpha: 0.16)
                                : selected
                                ? const Color(
                                    0xFFFF6B4A,
                                  ).withValues(alpha: 0.18)
                                : Colors.transparent,
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(
                              color: hasFocus
                                  ? const Color(0xFF5DE0C2)
                                  : selected
                                  ? const Color(
                                      0xFFFF6B4A,
                                    ).withValues(alpha: 0.5)
                                  : Colors.transparent,
                              width: hasFocus ? 2 : 1,
                            ),
                          ),
                          child: Row(
                            children: [
                              Icon(
                                Icons.label_outline_rounded,
                                size: 17,
                                color: hasFocus
                                    ? const Color(0xFF5DE0C2)
                                    : selected
                                    ? const Color(0xFFFF8B70)
                                    : const Color(0xFF8CAAA9),
                              ),
                              const SizedBox(width: 8),
                              Expanded(
                                child: Text(
                                  label,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: TextStyle(
                                    color: hasFocus || selected
                                        ? Colors.white
                                        : Colors.white70,
                                    fontSize: 12,
                                    fontWeight: selected
                                        ? FontWeight.w700
                                        : FontWeight.w500,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  );
                },
              ),
            ),
          );
        }),
      ),
    );
  }
}

class GridViewContentView<T> extends StatelessWidget {
  final List<T> items;
  final Widget Function(T) itemBuilder;

  /// Called when the user taps (or short-presses D-Pad select) on an item.
  final void Function(T)? onTap;

  /// Called when the user long-presses (or holds D-Pad select ≥600ms) on an item.
  final void Function(T)? onLongPress;
  final ReorderCallback? onReorder;
  final bool showReorderControls;
  final void Function(int, int)? onMoveItem;
  final FocusNode? Function(T item)? focusNodeForItem;

  /// FocusNode assigned to the first item so parent can focus it programmatically.
  final FocusNode? firstItemFocusNode;

  /// Called when the focused item is at the left edge of the grid and user presses left.
  final VoidCallback? onAtLeftEdge;

  /// Called when the focused item is at the right edge of the grid and user presses right.
  final VoidCallback? onAtRightEdge;

  const GridViewContentView({
    super.key,
    required this.items,
    required this.itemBuilder,
    this.onTap,
    this.onLongPress,
    this.onReorder,
    this.showReorderControls = false,
    this.onMoveItem,
    this.focusNodeForItem,
    this.firstItemFocusNode,
    this.onAtLeftEdge,
    this.onAtRightEdge,
  });

  @override
  Widget build(BuildContext context) {
    final width = MediaQuery.sizeOf(context).width;
    final crossAxisCount = width >= 1200
        ? 6
        : width >= 800
        ? 5
        : width >= 600
        ? 4
        : 2;

    if (items.isEmpty) {
      return const Center(
        child: Text(
          'No hay contenido disponible',
          style: TextStyle(color: Colors.white70),
        ),
      );
    }

    return GridView.builder(
      padding: const EdgeInsets.fromLTRB(12, 10, 12, 20),
      gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: crossAxisCount,
        childAspectRatio: 0.8,
        crossAxisSpacing: 10,
        mainAxisSpacing: 10,
      ),
      itemCount: items.length,
      itemBuilder: (context, index) {
        final item = items[index];
        final isFirstItem = index == 0;
        final isLeftEdge = index % crossAxisCount == 0;
        final isRightEdge =
            index % crossAxisCount == crossAxisCount - 1 ||
            index == items.length - 1;
        final isBottomEdge = index + crossAxisCount >= items.length;
        return _GridItem<T>(
          key: ValueKey(item.hashCode),
          item: item,
          itemBuilder: itemBuilder,
          onTap: onTap,
          onLongPress: onLongPress,
          focusNode:
              focusNodeForItem?.call(item) ??
              (isFirstItem ? firstItemFocusNode : null),
          onMoveLeft: onReorder != null && index > 0
              ? () => onReorder!(index, index - 1)
              : null,
          onMoveRight: onReorder != null && index < items.length - 1
              ? () => onReorder!(index, index + 2)
              : null,
          onEdgeLeft: isLeftEdge ? onAtLeftEdge : null,
          onEdgeRight: isRightEdge ? onAtRightEdge : null,
          isBottomEdge: isBottomEdge,
          autofocus: isFirstItem,
          showReorderControls: showReorderControls,
          onMoveItem: onMoveItem,
          itemIndex: index,
        );
      },
    );
  }
}

/// Handles both touch and D-Pad tap vs long-press for a single grid item.
/// For D-Pad: starts a 600ms timer on KeyDownEvent; fires onTap if key is
/// released before the timer, or onLongPress if the timer expires first.
class _GridItem<T> extends StatefulWidget {
  final T item;
  final Widget Function(T) itemBuilder;
  final void Function(T)? onTap;
  final void Function(T)? onLongPress;
  final FocusNode? focusNode;
  final bool autofocus;
  final bool showReorderControls;
  final void Function(int, int)? onMoveItem;
  final int itemIndex;
  final VoidCallback? onMoveLeft;
  final VoidCallback? onMoveRight;

  /// Called when focused item is at the left edge of a row and user presses left.
  final VoidCallback? onEdgeLeft;

  /// Called when focused item is at the right edge of a row and user presses right.
  final VoidCallback? onEdgeRight;
  final bool isBottomEdge;

  const _GridItem({
    super.key,
    required this.item,
    required this.itemBuilder,
    this.onTap,
    this.onLongPress,
    this.focusNode,
    this.autofocus = false,
    this.showReorderControls = false,
    this.onMoveItem,
    this.itemIndex = 0,
    this.onMoveLeft,
    this.onMoveRight,
    this.onEdgeLeft,
    this.onEdgeRight,
    this.isBottomEdge = false,
  });

  @override
  State<_GridItem<T>> createState() => _GridItemState<T>();
}

class _GridItemState<T> extends State<_GridItem<T>> {
  Timer? _longPressTimer;
  Timer? _initialFocusTimer;
  bool _longPressTriggered = false;
  bool _ignoreInitialSelect = false;
  bool _suppressNextTap = false;

  @override
  void initState() {
    super.initState();
    _ignoreInitialSelect = widget.autofocus;
    if (_ignoreInitialSelect) {
      _initialFocusTimer = Timer(const Duration(milliseconds: 500), () {
        _ignoreInitialSelect = false;
      });
    }
  }

  void _startLongPressTimer() {
    _longPressTriggered = false;
    _longPressTimer?.cancel();
    _longPressTimer = Timer(const Duration(milliseconds: 600), () {
      _longPressTriggered = true;
      _suppressNextTap = true;
      widget.onLongPress?.call(widget.item);
    });
  }

  void _handleGestureLongPress() {
    _suppressNextTap = true;
    widget.onLongPress?.call(widget.item);
  }

  void _handleTap() {
    if (_suppressNextTap) {
      _suppressNextTap = false;
      return;
    }
    widget.onTap?.call(widget.item);
  }

  void _cancelTimer() {
    _longPressTimer?.cancel();
    _longPressTimer = null;
  }

  @override
  void dispose() {
    _longPressTimer?.cancel();
    _initialFocusTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Focus(
      focusNode: widget.focusNode,
      autofocus: widget.autofocus,
      onKeyEvent: (node, event) {
        if (event is KeyDownEvent) {
          // Shift/Ctrl + arrow: reorder (favorites)
          if (HardwareKeyboard.instance.isShiftPressed ||
              HardwareKeyboard.instance.isControlPressed) {
            if (event.logicalKey == LogicalKeyboardKey.arrowLeft &&
                widget.onMoveLeft != null) {
              widget.onMoveLeft!();
              return KeyEventResult.handled;
            } else if (event.logicalKey == LogicalKeyboardKey.arrowRight &&
                widget.onMoveRight != null) {
              widget.onMoveRight!();
              return KeyEventResult.handled;
            }
          }
          // Edge navigation: left/right at row boundaries jumps to prev/next category
          if (event.logicalKey == LogicalKeyboardKey.arrowLeft &&
              widget.onEdgeLeft != null) {
            widget.onEdgeLeft!();
            return KeyEventResult.handled;
          }
          if (event.logicalKey == LogicalKeyboardKey.arrowRight &&
              widget.onEdgeRight != null) {
            widget.onEdgeRight!();
            return KeyEventResult.handled;
          }
          if (event.logicalKey == LogicalKeyboardKey.arrowDown &&
              widget.isBottomEdge) {
            return KeyEventResult.handled;
          }
          if (widget.showReorderControls &&
              (event.logicalKey == LogicalKeyboardKey.channelUp ||
                  event.logicalKey == LogicalKeyboardKey.pageUp)) {
            widget.onMoveItem?.call(widget.itemIndex, -1);
            return KeyEventResult.handled;
          }
          if (widget.showReorderControls &&
              (event.logicalKey == LogicalKeyboardKey.channelDown ||
                  event.logicalKey == LogicalKeyboardKey.pageDown)) {
            widget.onMoveItem?.call(widget.itemIndex, 1);
            return KeyEventResult.handled;
          }
        }

        final isSelectKey =
            event.logicalKey == LogicalKeyboardKey.select ||
            event.logicalKey == LogicalKeyboardKey.enter ||
            event.logicalKey == LogicalKeyboardKey.gameButtonSelect;

        if (!isSelectKey) return KeyEventResult.ignored;
        if (_ignoreInitialSelect) return KeyEventResult.handled;

        if (event is KeyDownEvent) {
          // First physical press — start the long-press timer
          _startLongPressTimer();
          return KeyEventResult.handled;
        }

        if (event is KeyUpEvent) {
          if (_longPressTriggered) {
            // Long press already fired — just clean up
            _cancelTimer();
          } else {
            // Released before timer — treat as tap
            _cancelTimer();
            _handleTap();
          }
          return KeyEventResult.handled;
        }

        // KeyRepeatEvent while holding — timer is already running, ignore
        return KeyEventResult.handled;
      },
      child: Builder(
        builder: (context) {
          final hasFocus = Focus.of(context).hasFocus;
          return GestureDetector(
            onTap: _handleTap,
            onLongPress: _handleGestureLongPress,
            child: Card(
              color: hasFocus
                  ? const Color(0xFFFF6B4A)
                  : const Color(0xFF15212A),
              margin: const EdgeInsets.all(2),
              elevation: hasFocus ? 8 : 2,
              shadowColor: const Color(0xFFFF6B4A).withValues(alpha: 0.25),
              clipBehavior: Clip.antiAlias,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(14),
                side: BorderSide(
                  color: hasFocus
                      ? Colors.white
                      : Colors.white.withValues(alpha: 0.08),
                  width: hasFocus ? 2 : 1,
                ),
              ),
              child: Stack(
                children: [
                  widget.itemBuilder(widget.item),
                  if (widget.showReorderControls)
                    const Positioned(
                      top: 4,
                      left: 4,
                      child: Icon(
                        Icons.swap_vert,
                        color: Colors.white70,
                        size: 18,
                      ),
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
