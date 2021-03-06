
// 绑定增量策略切换事件
function bindMappingIncrementStrategyConfigChange(){
    var $mappingIncrementStrategyConfig = $("#mappingIncrementStrategyConfig");
    var $radio = $mappingIncrementStrategyConfig.find('input:radio[type="radio"]');
    // 初始化icheck插件
    $radio.iCheck({
        labelHover : false,
        cursor : true,
        radioClass : 'iradio_flat-blue',
    }).on('ifChecked', function(event) {
        showIncrementStrategyConfig($(this).val());
    });

    // 渲染选择radio配置
    var value = $mappingIncrementStrategyConfig.find('input[type="radio"]:checked').val();
    showIncrementStrategyConfig(value);
}

// 显示增量策略配置（日志/定时）
function showIncrementStrategyConfig($value){
    var $dqlConfig = $("#mappingIncrementStrategyDQLConfig");
    var $quartzConfig = $("#mappingIncrementStrategyQuartzConfig");
    if('log' == $value){
        $quartzConfig.addClass("hidden");
        $dqlConfig.removeClass("hidden");
    }else{
        $dqlConfig.addClass("hidden");
        $quartzConfig.removeClass("hidden");
    }
}

$(function() {
    // 绑定增量策略切换事件
    bindMappingIncrementStrategyConfigChange();
});