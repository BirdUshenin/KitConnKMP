#!/usr/bin/env ruby
# Генерирует KitConnIOS.xcodeproj (xcodeproj из CocoaPods), чтобы не хранить pbxproj руками.
#   GEM_HOME=$(ls -d /opt/homebrew/Cellar/cocoapods/*/libexec | head -1) /opt/homebrew/opt/ruby/bin/ruby generate_project.rb
#   ... generate_project.rb            # по умолчанию: без расширения и Network Extension (бесплатный Apple ID)
#   ... generate_project.rb --tunnel   # с расширением PacketTunnel (нужна платная программа Apple Developer)
require 'xcodeproj'
require 'fileutils'

with_tunnel = ARGV.include?('--tunnel')
root = __dir__
path = File.join(root, 'KitConnIOS.xcodeproj')
FileUtils.rm_rf(path)
proj = Xcodeproj::Project.new(path)
config_ref = proj.main_group.new_reference('Config.xcconfig')

# ───────── приложение ─────────
target = proj.new_target(:application, 'KitConnIOS', :ios, '16.0')
group = proj.main_group.new_group('iosApp', 'iosApp')
%w[iOSApp.swift ContentView.swift Secrets.swift NetworkTunnelBridge.swift].each do |f|
  target.add_file_references([group.new_reference(f)])
end
group.new_reference('Info.plist')
target.resources_build_phase.add_file_reference(group.new_reference('Assets.xcassets'))

# Общий модуль собирает Gradle; фаза должна идти до компиляции Swift
phase = target.new_shell_script_build_phase('Build Kotlin framework')
phase.shell_script = <<~SH
  cd "$SRCROOT/.."
  ./gradlew :shared:embedAndSignAppleFrameworkForXcode
SH
phase.always_out_of_date = '1'
target.build_phases.move(phase, 0)

# ───────── расширение туннеля ─────────
ext = nil
if with_tunnel
  ext = proj.new_target(:app_extension, 'PacketTunnel', :ios, '16.0')
  ext_group = proj.main_group.new_group('PacketTunnel', 'PacketTunnel')
  %w[PacketTunnelProvider.swift XrayEngine.swift].each do |f|
    ext.add_file_references([ext_group.new_reference(f)])
  end
  %w[Info.plist PacketTunnel.entitlements README.md].each { |f| ext_group.new_reference(f) }

  target.add_dependency(ext)
  embed = target.new_copy_files_build_phase('Embed Foundation Extensions')
  embed.symbol_dst_subfolder_spec = :plug_ins
  build_file = embed.add_file_reference(ext.product_reference)
  build_file.settings = { 'ATTRIBUTES' => ['RemoveHeadersOnCopy'] }

  ext.build_configurations.each do |c|
    c.base_configuration_reference = config_ref
    s = c.build_settings
    s['PRODUCT_BUNDLE_IDENTIFIER'] = 'ru.kitconn.vpn.kmp.PacketTunnel'
    s['PRODUCT_NAME'] = '$(TARGET_NAME)'
    s['SWIFT_VERSION'] = '5.0'
    s['GENERATE_INFOPLIST_FILE'] = 'YES'
    s['INFOPLIST_FILE'] = 'PacketTunnel/Info.plist'
    s['MARKETING_VERSION'] = '4.0.0'
    s['CURRENT_PROJECT_VERSION'] = '4'
    s['SKIP_INSTALL'] = 'YES'
    s['CODE_SIGN_ENTITLEMENTS'] = 'PacketTunnel/PacketTunnel.entitlements'
    s['CODE_SIGN_STYLE'] = 'Automatic'
    s['TARGETED_DEVICE_FAMILY'] = '1'
    s['LD_RUNPATH_SEARCH_PATHS'] = ['$(inherited)', '@executable_path/Frameworks', '@executable_path/../../Frameworks']
  end
end

target.build_configurations.each do |config|
  config.base_configuration_reference = config_ref
  s = config.build_settings
  s['PRODUCT_BUNDLE_IDENTIFIER'] = 'ru.kitconn.vpn.kmp'
  s['PRODUCT_NAME'] = 'KitConn'
  s['SWIFT_VERSION'] = '5.0'
  s['GENERATE_INFOPLIST_FILE'] = 'YES'
  s['INFOPLIST_FILE'] = 'iosApp/Info.plist'
  s['INFOPLIST_KEY_CFBundleDisplayName'] = 'KitConn VPN'
  s['INFOPLIST_KEY_UILaunchScreen_Generation'] = 'YES'
  s['INFOPLIST_KEY_UISupportedInterfaceOrientations'] = 'UIInterfaceOrientationPortrait'
  s['INFOPLIST_KEY_UIUserInterfaceStyle'] = 'Dark'
  s['MARKETING_VERSION'] = '4.0.0'
  s['CURRENT_PROJECT_VERSION'] = '4'
  s['ASSETCATALOG_COMPILER_APPICON_NAME'] = 'AppIcon'
  s['TARGETED_DEVICE_FAMILY'] = '1'
  s['ENABLE_USER_SCRIPT_SANDBOXING'] = 'NO'
  s['FRAMEWORK_SEARCH_PATHS'] = '$(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)'
  s['OTHER_LDFLAGS'] = ['$(inherited)', '-framework', 'Shared']
  s['CODE_SIGN_STYLE'] = 'Automatic'
  # Общий модуль собирается только под Apple Silicon (iosArm64, iosSimulatorArm64)
  s['EXCLUDED_ARCHS[sdk=iphonesimulator*]'] = 'x86_64'
  if with_tunnel
    s['CODE_SIGN_ENTITLEMENTS'] = 'iosApp.entitlements'
    s['SWIFT_ACTIVE_COMPILATION_CONDITIONS'] = ['$(inherited)', 'TUNNEL']
  end
end

scheme = Xcodeproj::XCScheme.new
scheme.add_build_target(target)
scheme.set_launch_target(target)
scheme.save_as(path, 'KitConnIOS', true)
proj.save
puts "OK: #{path} (tunnel: #{with_tunnel})"
