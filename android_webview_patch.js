// Fix 1: Ép Blockbench ngừng sử dụng ImageBitmap (WebView GPU thường làm trong suốt object này)
window.createImageBitmap = undefined;

// Fix 2: Ép tất cả Canvas 2D Context sử dụng willReadFrequently
const originalGetContext = HTMLCanvasElement.prototype.getContext;
HTMLCanvasElement.prototype.getContext = function(type, attributes) {
    if (type === '2d') {
        attributes = attributes || {};
        attributes.willReadFrequently = true; // Ép fallback về CPU rendering cho canvas, fix lỗi đen/trong suốt
    }
    return originalGetContext.call(this, type, attributes);
};
