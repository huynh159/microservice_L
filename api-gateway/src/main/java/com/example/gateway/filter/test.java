@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered{
    private static final Logger log = LoggerFactory.getLogger(TraceIdGlobalFilter.class);
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain){
        //1. Lấy X-Trace-Id từ client gửi lên ( nếu có)
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);

        //2. Nếu chưa có, tự động sinh mã UUID mới duy nhất 
        if(traceId ==null || traceId.trim().isEmpty()){
            traceId == UUID.randomUUID().toString();
        } else {
            traceId = traceId.trim();
        }
        final String finalTraceId = traceId; 
        // 3. Biến đổi (mutate) request để đầy X-Trace-Id sang downsteam service
        ServerHttpRequest mutateRequest = exchang.getRequest().mutate()
            .header(TRACE_ID_HEADER, finalTraceId)
            .build();

        ServerWebExchange mutatedExchang = exchang.mutate()
            .request(mutatedRequest)
            .build();

        // 4. Đính kèm X-Trace-Id vào Http response header trả về cho client 
        mutatedExchange.getResponse().beforeCommit(() -> {
            mutatedExchange.getResponse().getHeaders().set(TRACE_ID_HEADER, finalTraceId);
            return Mono.empty();

        });
        //5. Chuyển giao request đã được làm giàu sang filter tiếp theo 
        return  chain.filer(mutatedExchange);

    }
    @Override
    public int getOrder(){
        // Ưu tiên chạy cao nhất để mọi filter phía sau đều có thể sẵn X-Trace-Id
        return Ordered.HIGHEST_PRECEDENCE;
    }
}