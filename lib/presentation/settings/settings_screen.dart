import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:iptv_flutter/core/storage/credential_store.dart';
import 'package:iptv_flutter/core/storage/shared_prefs_storage.dart';
import 'package:iptv_flutter/core/theme/app_theme.dart';
import 'package:iptv_flutter/data/models/live_category.dart';
import 'package:iptv_flutter/data/models/vod_category.dart';
import 'package:iptv_flutter/data/models/series_category.dart';
import 'package:iptv_flutter/data/api/xtream_api_client.dart';

class SettingsScreen extends StatefulWidget {
  final XtreamApiClient client;
  final List<LiveCategory> liveCategories;
  final List<VodCategory> vodCategories;
  final List<SeriesCategory> seriesCategories;
  final ValueChanged<XtreamApiClient> onSettingsSaved;

  const SettingsScreen({
    super.key,
    required this.client,
    required this.liveCategories,
    required this.vodCategories,
    required this.seriesCategories,
    required this.onSettingsSaved,
  });

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  final storage = SharedPrefsStorage();

  // IPTV Credentials
  late TextEditingController _urlController;
  late TextEditingController _userController;
  late TextEditingController _passController;

  final FocusNode _urlFocusNode = FocusNode();
  final FocusNode _userFocusNode = FocusNode();
  final FocusNode _passFocusNode = FocusNode();
  final FocusNode _sliderFocusNode = FocusNode();

  final List<FocusNode> _firstCategoryFocusNodes = [
    FocusNode(),
    FocusNode(),
    FocusNode(),
  ];

  // Auto-refresh & Appearance state
  int _autoRefreshDays = 0;
  double _scale = 1.0;
  int _density = 0;
  bool _contrast = false;
  bool _storeVisibleOnly = false;

  final Map<String, String> _colors = <String, String>{
    'ROJO': 'none',
    'VERDE': 'none',
    'AMARILLO': 'none',
    'AZUL': 'none',
  };

  static const Map<String, String> _actions = {
    'none': 'Sin asignar',
    'favorites': 'Favoritos',
    'search': 'Buscar',
    'live': 'Canales en vivo',
    'movies': 'Películas',
    'series': 'Series',
    'continueWatching': 'Seguir viendo',
  };

  // Live Categories ordering & visibility
  late List<LiveCategory> _liveCats;
  late Set<String> _hiddenLiveCatIds;

  // VOD Categories ordering & visibility
  late List<VodCategory> _vodCats;
  late Set<String> _hiddenVodCatIds;

  // Series Categories ordering & visibility
  late List<SeriesCategory> _seriesCats;
  late Set<String> _hiddenSeriesCatIds;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);

    _urlController = TextEditingController(
      text: CredentialStore.server.isEmpty
          ? widget.client.baseUrl
          : CredentialStore.server,
    );
    _userController = TextEditingController(
      text: CredentialStore.username.isEmpty
          ? widget.client.username
          : CredentialStore.username,
    );
    _passController = TextEditingController(
      text: CredentialStore.password.isEmpty
          ? widget.client.password
          : CredentialStore.password,
    );

    _urlController.addListener(_autoSaveCredentials);
    _userController.addListener(_autoSaveCredentials);
    _passController.addListener(_autoSaveCredentials);

    final refreshVal = storage.getString('settings_auto_refresh_days');
    if (refreshVal != null) {
      _autoRefreshDays = int.tryParse(refreshVal) ?? 0;
    } else {
      final oldMinStr = storage.getString('settings_auto_refresh_minutes');
      if (oldMinStr != null) {
        final min = int.tryParse(oldMinStr) ?? 0;
        _autoRefreshDays = (min / 1440).round();
      }
    }

    _scale =
        double.tryParse(storage.getString('settings_card_scale') ?? '') ?? 1.0;
    _density =
        int.tryParse(storage.getString('settings_grid_density') ?? '') ?? 0;
    _contrast = storage.getBool('settings_high_contrast') ?? false;
    _storeVisibleOnly = storage.getBool('settings_store_visible_only') ?? false;

    final savedColors = storage.getStringList('settings_color_actions') ?? [];
    var i = 0;
    for (final key in _colors.keys) {
      if (i < savedColors.length) _colors[key] = savedColors[i];
      i++;
    }

    _initCategories();
  }

  void _initCategories() {
    final hiddenLive = storage.getStringList('settings_live_cat_hidden') ?? [];
    _hiddenLiveCatIds = hiddenLive.toSet();
    final liveOrder = storage.getStringList('settings_live_cat_order') ?? [];
    _liveCats = List.from(widget.liveCategories);
    if (liveOrder.isNotEmpty) {
      _liveCats.sort((a, b) {
        final indexA = liveOrder.indexOf(a.categoryId.toString());
        final indexB = liveOrder.indexOf(b.categoryId.toString());
        if (indexA == -1 && indexB == -1) return 0;
        if (indexA == -1) return 1;
        if (indexB == -1) return -1;
        return indexA.compareTo(indexB);
      });
    }

    final hiddenVod = storage.getStringList('settings_vod_cat_hidden') ?? [];
    _hiddenVodCatIds = hiddenVod.toSet();
    final vodOrder = storage.getStringList('settings_vod_cat_order') ?? [];
    _vodCats = List.from(widget.vodCategories);
    if (vodOrder.isNotEmpty) {
      _vodCats.sort((a, b) {
        final indexA = vodOrder.indexOf(a.categoryId.toString());
        final indexB = vodOrder.indexOf(b.categoryId.toString());
        if (indexA == -1 && indexB == -1) return 0;
        if (indexA == -1) return 1;
        if (indexB == -1) return -1;
        return indexA.compareTo(indexB);
      });
    }

    final hiddenSeries =
        storage.getStringList('settings_series_cat_hidden') ?? [];
    _hiddenSeriesCatIds = hiddenSeries.toSet();
    final seriesOrder =
        storage.getStringList('settings_series_cat_order') ?? [];
    _seriesCats = List.from(widget.seriesCategories);
    if (seriesOrder.isNotEmpty) {
      _seriesCats.sort((a, b) {
        final indexA = seriesOrder.indexOf(a.categoryId.toString());
        final indexB = seriesOrder.indexOf(b.categoryId.toString());
        if (indexA == -1 && indexB == -1) return 0;
        if (indexA == -1) return 1;
        if (indexB == -1) return -1;
        return indexA.compareTo(indexB);
      });
    }
  }

  void _autoSaveCredentials() async {
    await CredentialStore.save(
      username: _userController.text.trim(),
      password: _passController.text.trim(),
      server: _urlController.text.trim(),
    );
  }

  void _notifySettingsSaved() {
    storage.setStringList('settings_color_actions', _colors.values.toList());
    widget.onSettingsSaved(
      XtreamApiClient(
        baseUrl: _urlController.text.trim(),
        username: _userController.text.trim(),
        password: _passController.text.trim(),
      ),
    );
  }

  Future<void> _updateAutoRefreshDays(int days) async {
    setState(() => _autoRefreshDays = days);
    await storage.setString('settings_auto_refresh_days', days.toString());
    await storage.setString(
      'settings_auto_refresh_minutes',
      (days * 1440).toString(),
    );
  }

  void _changeScale(int direction) {
    final values = [.8, .9, 1.0, 1.1, 1.2, 1.3];
    var index = values.indexOf(_scale);
    if (index < 0) index = 2;
    index = (index + direction).clamp(0, values.length - 1);
    setState(() => _scale = values[index]);
    storage.setString('settings_card_scale', _scale.toString());
  }

  Future<void> _saveCategoriesLive() async {
    await storage.setStringList(
      'settings_live_cat_hidden',
      _hiddenLiveCatIds.toList(),
    );
    await storage.setStringList(
      'settings_live_cat_order',
      _liveCats.map((e) => e.categoryId.toString()).toList(),
    );
  }

  Future<void> _saveCategoriesVod() async {
    await storage.setStringList(
      'settings_vod_cat_hidden',
      _hiddenVodCatIds.toList(),
    );
    await storage.setStringList(
      'settings_vod_cat_order',
      _vodCats.map((e) => e.categoryId.toString()).toList(),
    );
  }

  Future<void> _saveCategoriesSeries() async {
    await storage.setStringList(
      'settings_series_cat_hidden',
      _hiddenSeriesCatIds.toList(),
    );
    await storage.setStringList(
      'settings_series_cat_order',
      _seriesCats.map((e) => e.categoryId.toString()).toList(),
    );
  }

  @override
  void dispose() {
    _urlController.removeListener(_autoSaveCredentials);
    _userController.removeListener(_autoSaveCredentials);
    _passController.removeListener(_autoSaveCredentials);
    _tabController.dispose();
    _urlController.dispose();
    _userController.dispose();
    _passController.dispose();
    _urlFocusNode.dispose();
    _userFocusNode.dispose();
    _passFocusNode.dispose();
    _sliderFocusNode.dispose();
    for (final node in _firstCategoryFocusNodes) {
      node.dispose();
    }
    super.dispose();
  }

  void _switchTab(int delta) {
    int nextIndex = _tabController.index + delta;
    if (nextIndex >= 0 && nextIndex < _tabController.length) {
      setState(() {
        _tabController.animateTo(nextIndex);
      });
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _firstCategoryFocusNodes[nextIndex].requestFocus();
      });
    }
  }

  KeyEventResult _handleFormFieldKey({
    required KeyEvent event,
    FocusNode? nextFocus,
    FocusNode? prevFocus,
  }) {
    if (event is KeyDownEvent) {
      if (event.logicalKey == LogicalKeyboardKey.arrowDown &&
          nextFocus != null) {
        nextFocus.requestFocus();
        return KeyEventResult.handled;
      }
      if (event.logicalKey == LogicalKeyboardKey.arrowUp && prevFocus != null) {
        prevFocus.requestFocus();
        return KeyEventResult.handled;
      }
    }
    return KeyEventResult.ignored;
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      onPopInvokedWithResult: (_, __) {
        _notifySettingsSaved();
      },
      child: Scaffold(
        backgroundColor: AppColors.ink,
        body: FocusTraversalGroup(
          policy: ReadingOrderTraversalPolicy(),
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildSectionHeader('Credenciales IPTV'),
                const SizedBox(height: 12),
                _buildCard([
                  Focus(
                    onKeyEvent: (node, event) => _handleFormFieldKey(
                      event: event,
                      nextFocus: _userFocusNode,
                    ),
                    child: TextFormField(
                      controller: _urlController,
                      focusNode: _urlFocusNode,
                      autofocus: true,
                      textInputAction: TextInputAction.next,
                      style: const TextStyle(color: Colors.white),
                      decoration: InputDecoration(
                        labelText: 'URL Servidor',
                        labelStyle: TextStyle(color: AppColors.bodyText),
                        prefixIcon: Icon(Icons.link, color: AppColors.mint),
                        border: OutlineInputBorder(),
                      ),
                      onFieldSubmitted: (_) => _userFocusNode.requestFocus(),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Focus(
                    onKeyEvent: (node, event) => _handleFormFieldKey(
                      event: event,
                      nextFocus: _passFocusNode,
                      prevFocus: _urlFocusNode,
                    ),
                    child: TextFormField(
                      controller: _userController,
                      focusNode: _userFocusNode,
                      textInputAction: TextInputAction.next,
                      style: const TextStyle(color: Colors.white),
                      decoration: InputDecoration(
                        labelText: 'Usuario',
                        labelStyle: TextStyle(color: AppColors.bodyText),
                        prefixIcon: Icon(
                          Icons.person,
                          color: AppColors.mint,
                        ),
                        border: OutlineInputBorder(),
                      ),
                      onFieldSubmitted: (_) => _passFocusNode.requestFocus(),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Focus(
                    onKeyEvent: (node, event) => _handleFormFieldKey(
                      event: event,
                      nextFocus: _sliderFocusNode,
                      prevFocus: _userFocusNode,
                    ),
                    child: TextFormField(
                      controller: _passController,
                      focusNode: _passFocusNode,
                      textInputAction: TextInputAction.next,
                      obscureText: true,
                      style: const TextStyle(color: Colors.white),
                      decoration: InputDecoration(
                        labelText: 'Contraseña',
                        labelStyle: TextStyle(color: AppColors.bodyText),
                        prefixIcon: Icon(Icons.lock, color: AppColors.mint),
                        border: OutlineInputBorder(),
                      ),
                      onFieldSubmitted: (_) => _sliderFocusNode.requestFocus(),
                    ),
                  ),
                ]),

                const SizedBox(height: 28),
                _buildSectionHeader('Apariencia y accesos'),
                const SizedBox(height: 12),
                _buildCard([
                  const Text(
                    'Tamaño de tarjetas',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Focus(
                    focusNode: _sliderFocusNode,
                    onKeyEvent: (_, event) {
                      if (event is KeyDownEvent) {
                        if (event.logicalKey == LogicalKeyboardKey.arrowLeft) {
                          _changeScale(-1);
                          return KeyEventResult.handled;
                        }
                        if (event.logicalKey == LogicalKeyboardKey.arrowRight) {
                          _changeScale(1);
                          return KeyEventResult.handled;
                        }
                      }
                      return KeyEventResult.ignored;
                    },
                    child: Builder(
                      builder: (context) {
                        final hasFocus = Focus.of(context).hasFocus;
                        return AnimatedContainer(
                          duration: const Duration(milliseconds: 150),
                          padding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 4,
                          ),
                          decoration: BoxDecoration(
                            color: hasFocus
                                ? AppColors.focusFill
                                : Colors.transparent,
                            border: Border.all(
                              color: hasFocus
                                  ? AppColors.mint
                                  : Colors.white24,
                              width: hasFocus ? 2 : 1,
                            ),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Slider(
                            value: _scale,
                            min: .8,
                            max: 1.3,
                            divisions: 5,
                            activeColor: AppColors.mint,
                            onChanged: (value) {
                              setState(() => _scale = value);
                              storage.setString(
                                'settings_card_scale',
                                value.toString(),
                              );
                            },
                          ),
                        );
                      },
                    ),
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    'Densidad de cuadrícula',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Focus(
                    child: Builder(
                      builder: (context) {
                        final hasFocus = Focus.of(context).hasFocus;
                        return AnimatedContainer(
                          duration: const Duration(milliseconds: 150),
                          padding: const EdgeInsets.symmetric(horizontal: 12),
                          decoration: BoxDecoration(
                            color: hasFocus
                                ? AppColors.focusFill
                                : Colors.transparent,
                            border: Border.all(
                              color: hasFocus
                                  ? AppColors.mint
                                  : Colors.white24,
                              width: hasFocus ? 2 : 1,
                            ),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: DropdownButton<int>(
                            value: _density,
                            isExpanded: true,
                            underline: const SizedBox(),
                            dropdownColor: AppColors.panel,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 16,
                            ),
                            items: const [
                              DropdownMenuItem(
                                value: 0,
                                child: Text('Automática'),
                              ),
                              DropdownMenuItem(value: 1, child: Text('Cómoda')),
                              DropdownMenuItem(value: 2, child: Text('Densa')),
                            ],
                            onChanged: (value) {
                              if (value != null) {
                                setState(() => _density = value);
                                storage.setString(
                                  'settings_grid_density',
                                  value.toString(),
                                );
                              }
                            },
                          ),
                        );
                      },
                    ),
                  ),
                  const SizedBox(height: 12),
                  SwitchListTile(
                    title: const Text(
                      'Modo alto contraste',
                      style: TextStyle(color: Colors.white),
                    ),
                    value: _contrast,
                    activeThumbColor: AppColors.mint,
                    onChanged: (value) {
                      setState(() => _contrast = value);
                      storage.setBool('settings_high_contrast', value);
                    },
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    'Botones de color del mando',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 8),
                  ..._colors.keys.map(_colorRow),
                ]),

                const SizedBox(height: 28),
                _buildSectionHeader('Actualización Automática'),
                const SizedBox(height: 12),
                _FocusableDropdown(
                  value: _autoRefreshDays,
                  onChanged: (val) {
                    if (val != null) {
                      _updateAutoRefreshDays(val);
                    }
                  },
                ),
                const SizedBox(height: 12),
                SwitchListTile(
                  title: const Text(
                    'Guardar solo el contenido visible',
                    style: TextStyle(color: Colors.white),
                  ),
                  subtitle: const Text(
                    'Reduce el almacenamiento local usando las categorías mostradas.',
                    style: TextStyle(color: Colors.white60),
                  ),
                  value: _storeVisibleOnly,
                  activeThumbColor: AppColors.mint,
                  onChanged: (value) async {
                    setState(() => _storeVisibleOnly = value);
                    await storage.setBool('settings_store_visible_only', value);
                  },
                ),

                const SizedBox(height: 28),
                _buildSectionHeader('Gestión de Categorías'),
                const SizedBox(height: 12),
                Container(
                  decoration: BoxDecoration(
                    color: AppColors.panel,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(
                      color: Colors.white.withValues(alpha: 0.08),
                    ),
                  ),
                  child: Column(
                    children: [
                      TabBar(
                        controller: _tabController,
                        indicatorColor: AppColors.mint,
                        labelColor: AppColors.mint,
                        unselectedLabelColor: Colors.white60,
                        tabs: const [
                          Tab(text: 'En Vivo'),
                          Tab(text: 'Películas'),
                          Tab(text: 'Series'),
                        ],
                      ),
                      SizedBox(
                        height: 380,
                        child: TabBarView(
                          controller: _tabController,
                          children: [
                            _buildLiveCategoryTab(),
                            _buildVodCategoryTab(),
                            _buildSeriesCategoryTab(),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 24),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _colorRow(String key) => Focus(
    onKeyEvent: (node, event) {
      if (event is KeyDownEvent) {
        if (event.logicalKey == LogicalKeyboardKey.select ||
            event.logicalKey == LogicalKeyboardKey.enter ||
            event.logicalKey == LogicalKeyboardKey.gameButtonSelect) {
          final actionKeys = _actions.keys.toList();
          final currentIndex = actionKeys.indexOf(_colors[key] ?? 'none');
          final nextIndex = (currentIndex + 1) % actionKeys.length;
          final nextValue = actionKeys[nextIndex];

          setState(() => _colors[key] = nextValue);
          storage.setStringList(
            'settings_color_actions',
            _colors.values.toList(),
          );
          return KeyEventResult.handled;
        }
      }
      return KeyEventResult.ignored;
    },
    child: Builder(
      builder: (context) {
        final hasFocus = Focus.of(context).hasFocus;
        return AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
          decoration: BoxDecoration(
            color: hasFocus ? AppColors.focusFill : Colors.transparent,
            borderRadius: BorderRadius.circular(8),
            border: Border.all(
              color: hasFocus ? AppColors.mint : Colors.transparent,
              width: 2,
            ),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Icon(Icons.circle, color: _color(key), size: 18),
                  const SizedBox(width: 12),
                  Text(
                    'Botón $key',
                    style: const TextStyle(color: Colors.white, fontSize: 16),
                  ),
                ],
              ),
              DropdownButton<String>(
                value: _colors[key],
                dropdownColor: AppColors.panel,
                underline: const SizedBox(),
                style: const TextStyle(color: Colors.white, fontSize: 16),
                items: _actions.entries
                    .map(
                      (entry) => DropdownMenuItem(
                        value: entry.key,
                        child: Text(entry.value),
                      ),
                    )
                    .toList(),
                onChanged: (value) {
                  if (value != null) {
                    setState(() => _colors[key] = value);
                    storage.setStringList(
                      'settings_color_actions',
                      _colors.values.toList(),
                    );
                  }
                },
              ),
            ],
          ),
        );
      },
    ),
  );

  Color _color(String key) => {
    'ROJO': Colors.redAccent,
    'VERDE': Colors.greenAccent,
    'AMARILLO': Colors.amber,
    'AZUL': Colors.blueAccent,
  }[key]!;

  Widget _buildSectionHeader(String title) {
    return Text(
      title,
      style: const TextStyle(
        color: Colors.white,
        fontSize: 18,
        fontWeight: FontWeight.bold,
      ),
    );
  }

  Widget _buildCard(List<Widget> children) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.panel,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: children,
      ),
    );
  }

  Widget _buildCategoryHeaderButtons({
    required VoidCallback onShowAll,
    required VoidCallback onHideAll,
  }) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Row(
        children: [
          Expanded(
            child: _FocusableActionButton(
              label: 'Mostrar Todos',
              icon: Icons.visibility,
              onPressed: onShowAll,
              onSideTabChange: _switchTab,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: _FocusableActionButton(
              label: 'Ocultar Todos',
              icon: Icons.visibility_off,
              onPressed: onHideAll,
              onSideTabChange: _switchTab,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildLiveCategoryTab() {
    if (_liveCats.isEmpty) {
      return Center(
        child: Text(
          'No hay categorías',
          style: TextStyle(color: AppColors.subtleText),
        ),
      );
    }
    return Column(
      children: [
        _buildCategoryHeaderButtons(
          onShowAll: () {
            setState(() {
              _hiddenLiveCatIds.clear();
            });
            _saveCategoriesLive();
          },
          onHideAll: () {
            setState(() {
              _hiddenLiveCatIds = _liveCats
                  .map((c) => c.categoryId.toString())
                  .toSet();
            });
            _saveCategoriesLive();
          },
        ),
        const Divider(color: Colors.white12, height: 1),
        Expanded(
          child: ListView.builder(
            itemCount: _liveCats.length,
            itemBuilder: (context, index) {
              final cat = _liveCats[index];
              final idStr = cat.categoryId.toString();
              final isVisible = !_hiddenLiveCatIds.contains(idStr);

              return _FocusableCategoryTile(
                key: ValueKey(idStr),
                focusNode: index == 0 ? _firstCategoryFocusNodes[0] : null,
                title: cat.categoryName,
                value: isVisible,
                onSideTabChange: _switchTab,
                onChanged: (val) {
                  setState(() {
                    if (val) {
                      _hiddenLiveCatIds.remove(idStr);
                    } else {
                      _hiddenLiveCatIds.add(idStr);
                    }
                  });
                  _saveCategoriesLive();
                },
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _buildVodCategoryTab() {
    if (_vodCats.isEmpty) {
      return Center(
        child: Text(
          'No hay categorías',
          style: TextStyle(color: AppColors.subtleText),
        ),
      );
    }
    return Column(
      children: [
        _buildCategoryHeaderButtons(
          onShowAll: () {
            setState(() {
              _hiddenVodCatIds.clear();
            });
            _saveCategoriesVod();
          },
          onHideAll: () {
            setState(() {
              _hiddenVodCatIds = _vodCats
                  .map((c) => c.categoryId.toString())
                  .toSet();
            });
            _saveCategoriesVod();
          },
        ),
        const Divider(color: Colors.white12, height: 1),
        Expanded(
          child: ListView.builder(
            itemCount: _vodCats.length,
            itemBuilder: (context, index) {
              final cat = _vodCats[index];
              final idStr = cat.categoryId.toString();
              final isVisible = !_hiddenVodCatIds.contains(idStr);

              return _FocusableCategoryTile(
                key: ValueKey(idStr),
                focusNode: index == 0 ? _firstCategoryFocusNodes[1] : null,
                title: cat.categoryName,
                value: isVisible,
                onSideTabChange: _switchTab,
                onChanged: (val) {
                  setState(() {
                    if (val) {
                      _hiddenVodCatIds.remove(idStr);
                    } else {
                      _hiddenVodCatIds.add(idStr);
                    }
                  });
                  _saveCategoriesVod();
                },
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _buildSeriesCategoryTab() {
    if (_seriesCats.isEmpty) {
      return Center(
        child: Text(
          'No hay categorías',
          style: TextStyle(color: AppColors.subtleText),
        ),
      );
    }
    return Column(
      children: [
        _buildCategoryHeaderButtons(
          onShowAll: () {
            setState(() {
              _hiddenSeriesCatIds.clear();
            });
            _saveCategoriesSeries();
          },
          onHideAll: () {
            setState(() {
              _hiddenSeriesCatIds = _seriesCats
                  .map((c) => c.categoryId.toString())
                  .toSet();
            });
            _saveCategoriesSeries();
          },
        ),
        const Divider(color: Colors.white12, height: 1),
        Expanded(
          child: ListView.builder(
            itemCount: _seriesCats.length,
            itemBuilder: (context, index) {
              final cat = _seriesCats[index];
              final idStr = cat.categoryId.toString();
              final isVisible = !_hiddenSeriesCatIds.contains(idStr);

              return _FocusableCategoryTile(
                key: ValueKey(idStr),
                focusNode: index == 0 ? _firstCategoryFocusNodes[2] : null,
                title: cat.categoryName,
                value: isVisible,
                onSideTabChange: _switchTab,
                onChanged: (val) {
                  setState(() {
                    if (val) {
                      _hiddenSeriesCatIds.remove(idStr);
                    } else {
                      _hiddenSeriesCatIds.add(idStr);
                    }
                  });
                  _saveCategoriesSeries();
                },
              );
            },
          ),
        ),
      ],
    );
  }
}

class _FocusableDropdown extends StatefulWidget {
  final int value;
  final ValueChanged<int?> onChanged;

  const _FocusableDropdown({required this.value, required this.onChanged});

  @override
  State<_FocusableDropdown> createState() => _FocusableDropdownState();
}

class _FocusableDropdownState extends State<_FocusableDropdown> {
  bool _hasFocus = false;

  @override
  Widget build(BuildContext context) {
    return Focus(
      onFocusChange: (focused) => setState(() => _hasFocus = focused),
      onKeyEvent: (node, event) {
        if (event is KeyDownEvent) {
          if (event.logicalKey == LogicalKeyboardKey.select ||
              event.logicalKey == LogicalKeyboardKey.enter ||
              event.logicalKey == LogicalKeyboardKey.gameButtonSelect) {
            int nextVal = 0;
            if (widget.value == 0) {
              nextVal = 1;
            } else if (widget.value == 1) {
              nextVal = 3;
            } else if (widget.value == 3) {
              nextVal = 7;
            } else {
              nextVal = 0;
            }
            widget.onChanged(nextVal);
            return KeyEventResult.handled;
          }
        }
        return KeyEventResult.ignored;
      },
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        decoration: BoxDecoration(
          color: _hasFocus ? AppColors.focusFill : AppColors.panel,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: _hasFocus
                ? AppColors.mint
                : Colors.white.withValues(alpha: 0.08),
            width: 2,
          ),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            const Text(
              'Intervalo de actualización',
              style: TextStyle(color: Colors.white, fontSize: 16),
            ),
            DropdownButton<int>(
              value: widget.value,
              dropdownColor: AppColors.panel,
              underline: const SizedBox(),
              style: const TextStyle(color: Colors.white, fontSize: 16),
              items: const [
                DropdownMenuItem(value: 0, child: Text('Nunca')),
                DropdownMenuItem(value: 1, child: Text('Cada 1 Día')),
                DropdownMenuItem(value: 3, child: Text('Cada 3 Días')),
                DropdownMenuItem(value: 7, child: Text('Cada 7 Días')),
              ],
              onChanged: widget.onChanged,
            ),
          ],
        ),
      ),
    );
  }
}

class _FocusableActionButton extends StatefulWidget {
  final String label;
  final IconData icon;
  final VoidCallback onPressed;
  final ValueChanged<int>? onSideTabChange;

  const _FocusableActionButton({
    required this.label,
    required this.icon,
    required this.onPressed,
    this.onSideTabChange,
  });

  @override
  State<_FocusableActionButton> createState() => _FocusableActionButtonState();
}

class _FocusableActionButtonState extends State<_FocusableActionButton> {
  bool _hasFocus = false;

  @override
  Widget build(BuildContext context) {
    return Focus(
      onFocusChange: (focused) => setState(() => _hasFocus = focused),
      onKeyEvent: (node, event) {
        if (event is KeyDownEvent) {
          if (event.logicalKey == LogicalKeyboardKey.arrowLeft) {
            widget.onSideTabChange?.call(-1);
            return KeyEventResult.handled;
          } else if (event.logicalKey == LogicalKeyboardKey.arrowRight) {
            widget.onSideTabChange?.call(1);
            return KeyEventResult.handled;
          } else if (event.logicalKey == LogicalKeyboardKey.select ||
              event.logicalKey == LogicalKeyboardKey.enter ||
              event.logicalKey == LogicalKeyboardKey.gameButtonSelect) {
            widget.onPressed();
            return KeyEventResult.handled;
          }
        }
        return KeyEventResult.ignored;
      },
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
            color: _hasFocus ? AppColors.mint : Colors.white24,
            width: _hasFocus ? 2 : 1,
          ),
          color: _hasFocus ? AppColors.focusFill : Colors.transparent,
        ),
        child: OutlinedButton.icon(
          style: OutlinedButton.styleFrom(
            foregroundColor: Colors.white,
            side: BorderSide.none,
            minimumSize: const Size(220, 54),
            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
            alignment: Alignment.centerLeft,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(8),
            ),
          ),
          icon: Icon(
            widget.icon,
            size: 18,
            color: _hasFocus ? AppColors.mint : Colors.white,
          ),
          label: Text(
            widget.label,
            style: TextStyle(
              fontSize: 13,
              fontWeight: _hasFocus ? FontWeight.bold : FontWeight.normal,
            ),
          ),
          onPressed: widget.onPressed,
        ),
      ),
    );
  }
}

class _FocusableCategoryTile extends StatefulWidget {
  final String title;
  final bool value;
  final ValueChanged<bool> onChanged;
  final ValueChanged<int>? onSideTabChange;
  final FocusNode? focusNode;

  const _FocusableCategoryTile({
    super.key,
    required this.title,
    required this.value,
    required this.onChanged,
    this.onSideTabChange,
    this.focusNode,
  });

  @override
  State<_FocusableCategoryTile> createState() => _FocusableCategoryTileState();
}

class _FocusableCategoryTileState extends State<_FocusableCategoryTile> {
  bool _hasFocus = false;

  @override
  Widget build(BuildContext context) {
    return Focus(
      focusNode: widget.focusNode,
      onFocusChange: (focused) => setState(() => _hasFocus = focused),
      onKeyEvent: (node, event) {
        if (event is KeyDownEvent) {
          if (event.logicalKey == LogicalKeyboardKey.arrowLeft) {
            widget.onSideTabChange?.call(-1);
            return KeyEventResult.handled;
          } else if (event.logicalKey == LogicalKeyboardKey.arrowRight) {
            widget.onSideTabChange?.call(1);
            return KeyEventResult.handled;
          } else if (event.logicalKey == LogicalKeyboardKey.select ||
              event.logicalKey == LogicalKeyboardKey.enter ||
              event.logicalKey == LogicalKeyboardKey.gameButtonSelect) {
            widget.onChanged(!widget.value);
            return KeyEventResult.handled;
          }
        }
        return KeyEventResult.ignored;
      },
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
        decoration: BoxDecoration(
          color: _hasFocus ? AppColors.focusFill : Colors.transparent,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
            color: _hasFocus ? AppColors.mint : Colors.transparent,
            width: 2,
          ),
        ),
        child: SwitchListTile(
          title: Text(
            widget.title,
            style: TextStyle(
              color: Colors.white,
              fontWeight: _hasFocus ? FontWeight.bold : FontWeight.normal,
            ),
          ),
          value: widget.value,
          activeThumbColor: AppColors.mint,
          secondary: Icon(
            Icons.visibility,
            color: _hasFocus ? AppColors.mint : AppColors.faintText,
          ),
          onChanged: (val) => widget.onChanged(val),
        ),
      ),
    );
  }
}
