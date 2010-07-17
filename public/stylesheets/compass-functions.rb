require 'ostruct'

module Compass
  def self.configuration    
    @configuration ||= Configuration.new(:http_images_path => "/public/images/")
  end
end

class Compass::Configuration < OpenStruct
  def relative_assets?
    false
  end
end

module Compass::SassExtensions
end

module Compass::SassExtensions::Functions
end

%w(display enumerate font_files gradient_support image_size inline_image selectors urls).each do |func|
  require File.join(File.dirname(__FILE__), "compass/functions/#{func}")
end

module Sass::Script::Functions
  include Compass::SassExtensions::Functions::Display
  include Compass::SassExtensions::Functions::Enumerate
  include Compass::SassExtensions::Functions::FontFiles
  include Compass::SassExtensions::Functions::GradientSupport
  include Compass::SassExtensions::Functions::ImageSize
  include Compass::SassExtensions::Functions::InlineImage
  include Compass::SassExtensions::Functions::Selectors
  include Compass::SassExtensions::Functions::Urls
end

# Wierd that this has to be re-included to pick up sub-modules. Ruby bug?
class Sass::Script::Functions::EvaluationContext
  include Sass::Script::Functions
end
