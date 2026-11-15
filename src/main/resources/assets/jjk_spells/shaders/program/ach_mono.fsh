#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 c = texture(DiffuseSampler, texCoord);
    float gray = dot(c.rgb, vec3(0.299, 0.587, 0.114));
    // keep strongly red and violet pixels, drain everything else
    float red = clamp((c.r - max(c.g, c.b)) * 3.0, 0.0, 1.0);
    float violet = clamp((min(c.r, c.b) - c.g) * 3.0, 0.0, 1.0);
    float keep = max(red, violet);
    vec3 mono = vec3(gray * 0.9);
    fragColor = vec4(mix(mono, c.rgb, keep), 1.0);
}
