import 'package:iptv_flutter/data/models/iptv_user_info.dart';

abstract class RepositoryInterface {
  Future<IptvUserInfo> getUserInfo();
  Future<void> saveUserInfo(IptvUserInfo user);
  Future<void> clearUserInfo();
}
